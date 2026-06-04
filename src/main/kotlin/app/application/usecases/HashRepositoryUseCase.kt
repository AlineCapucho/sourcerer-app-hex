package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.GitRepositoryPort
import app.application.ports.LoggerPort
import app.application.ports.ServerApiPort
import app.domain.entities.*
import app.domain.errors.EmptyRepoException
import app.domain.errors.HashingException
import app.domain.errors.InvalidRepoException
import app.domain.services.*
import app.domain.valueobjects.ProcessEntry
import java.util.concurrent.TimeUnit

/**
 * Use case: Hash a local repository and upload stats to the server.
 *
 * This is the main orchestration use case. It coordinates:
 * - Validating the repository
 * - Crawling commit history
 * - Extracting statistics per commit (CommitHasher)
 * - Calculating per-author facts (FactHasher)
 * - Calculating meta facts (MetaHasher)
 * - Computing code longevity (CodeLongevity)
 * - Computing author distances (AuthorDistance)
 * - Sending results to the server API
 */
class HashRepositoryUseCase(
    private val serverApi: ServerApiPort,
    private val gitRepository: GitRepositoryPort,
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort,
    private val languageDetector: LanguageDetectionService,
    private val factHashingService: FactHashingService? = null,
    private val metaHashingService: MetaHashingService? = null,
    private val codeLongevityService: CodeLongevityService? = null,
    private val authorDistanceService: AuthorDistanceService? = null,
    private val commitHasherEnabled: Boolean = true,
    private val factHasherEnabled: Boolean = true,
    private val longevityEnabled: Boolean = false,
    private val metaHasherEnabled: Boolean = true,
    private val distancesEnabled: Boolean = true
) {

    fun execute(localRepo: LocalRepo) {
        logger.debug { "HashRepositoryUseCase.execute: $localRepo" }
        val processEntryId = localRepo.processEntryId

        if (!gitRepository.isValidRepo(localRepo.path)) {
            throw InvalidRepoException(localRepo.path)
        }

        try {
            logger.info { "Hashing of repo started" }
            updateProcess(processEntryId, ServerApiPort.PROCESS_STATUS_START)

            // Fetch rehashes and authors from git history.
            val (rehashes, authors, commitsCount) =
                gitRepository.fetchRehashesAndAuthors(localRepo.path)

            // Parse git config for remote origin info.
            gitRepository.parseGitConfig(localRepo.path, localRepo)

            // Calculate repo rehash and create server repo.
            val serverRepo = initServerRepo(localRepo, rehashes.last(),
                processEntryId)

            // Get repo setup (commits, emails to hash) from server.
            postRepoFromServer(serverRepo)

            // Delete locally missing commits from server.
            deleteStaleCommits(serverRepo, rehashes)

            // Send all repo emails for invites.
            postAuthorsToServer(authors, serverRepo)

            // Choose emails to filter commits with.
            val emails = authors.map { author -> author.email }.toHashSet()
            val filteredEmails = if (localRepo.hashAllContributors) {
                emails
            } else {
                filterEmails(emails, serverRepo)
            }

            // Common error handling.
            val errors = mutableListOf<Throwable>()
            val onError: (Throwable) -> Unit = { e ->
                errors.add(e)
                logger.error(e, "Hashing error")
            }

            // Get observable stream of commits.
            val observable = gitRepository.getCommitsObservable(
                localRepo.path, serverRepo, filteredEmails,
                extractCoauthors = true)

            // Use a connectable observable so multiple subscribers can share it.
            val connectableObservable = observable.publish()

            // --- CommitHasher: extract stats and send to server ---
            if (commitHasherEnabled) {
                val knownCommits = serverRepo.commits.toHashSet()
                connectableObservable
                    .filter { commit -> !knownCommits.contains(commit) }
                    .filter { commit -> filteredEmails.contains(commit.author.email) }
                    .map { commit ->
                        logger.printCommitDetail("Extracting stats")
                        commit.stats = CommitStatsExtractorService.extract(
                            commit.diffs, languageDetector)
                        val statsNumStr = if (commit.stats.isNotEmpty()) {
                            commit.stats.size.toString()
                        } else "No"
                        logger.printCommitDetail("$statsNumStr technology stats found")
                        commit
                    }
                    .buffer(20, TimeUnit.SECONDS, 1000)
                    .subscribe({ commitsBundle ->
                        val coauthorsCommits = commitsBundle
                            .filter { it.coauthors.isNotEmpty() }
                            .fold(mutableListOf<Commit>()) { acc, commit ->
                                acc.addAll(commit.coauthors.map { coauthor ->
                                    val newCommit = commit.copy()
                                    newCommit.author = coauthor
                                    newCommit
                                })
                                acc
                            }
                        val allCommits = commitsBundle + coauthorsCommits
                        if (allCommits.isNotEmpty()) {
                            serverApi.postCommits(allCommits).onErrorThrow()
                            logger.info { "Sent ${allCommits.size} commits to server" }
                        }
                    }, onError)
            }

            // --- FactHasher: calculate per-author statistical facts ---
            if (factHasherEnabled && factHashingService != null) {
                factHashingService.updateFromObservable(
                    connectableObservable, serverRepo.rehash, rehashes,
                    filteredEmails, onError)
            }

            // Start and synchronously wait until all subscribers complete.
            logger.print("Stats computation. May take a while...")
            connectableObservable.connect()

            // Send computed facts to server.
            if (factHasherEnabled && factHashingService != null) {
                val facts = factHashingService.getComputedFacts()
                if (facts.isNotEmpty()) {
                    serverApi.postFacts(facts).onErrorThrow()
                    logger.info { "Sent ${facts.size} facts to server" }
                }
            }

            // --- CodeLongevity ---
            if (longevityEnabled && codeLongevityService != null) {
                try {
                    val longevityFacts = codeLongevityService
                        .calculateLongevityFacts(localRepo.path,
                            serverRepo.rehash, filteredEmails)
                    if (longevityFacts.isNotEmpty()) {
                        serverApi.postFacts(longevityFacts).onErrorThrow()
                        logger.info { "Sent ${longevityFacts.size} longevity facts to server" }
                    }
                } catch (e: Throwable) {
                    onError(e)
                }
            }

            // --- MetaHasher ---
            if (metaHasherEnabled && metaHashingService != null) {
                try {
                    val userEmails = configurator.getUser().emails
                        .map { it.email }
                    val metaFacts = metaHashingService.calculateMetaFacts(
                        repoRehash = serverRepo.rehash,
                        authors = authors,
                        commitsCount = commitsCount,
                        userEmails = userEmails)
                    if (metaFacts.isNotEmpty()) {
                        serverApi.postFacts(metaFacts).onErrorThrow()
                        logger.info { "Sent ${metaFacts.size} meta facts to server" }
                    }
                } catch (e: Throwable) {
                    onError(e)
                }
            }

            // --- AuthorDistance ---
            if (distancesEnabled && authorDistanceService != null) {
                try {
                    val userEmails = configurator.getUser().emails
                        .map { it.email }.toHashSet()
                    val commitPathData = gitRepository
                        .getCommitPathData(localRepo.path)
                    val distances = authorDistanceService.calculateDistances(
                        commitPathData, serverRepo.rehash, emails, userEmails)
                    if (distances.isNotEmpty()) {
                        serverApi.postAuthorDistances(distances).onErrorThrow()
                        logger.info { "Sent ${distances.size} author distances to server" }
                    }
                } catch (e: Throwable) {
                    onError(e)
                }
            }

            if (errors.isNotEmpty()) {
                throw HashingException(errors)
            }

            logger.info("hashing_repo_success") { "Hashing repo completed" }
            updateProcess(processEntryId, ServerApiPort.PROCESS_STATUS_COMPLETE)

        } catch (e: EmptyRepoException) {
            updateProcess(processEntryId, ServerApiPort.PROCESS_STATUS_FAIL,
                ServerApiPort.PROCESS_ERROR_EMPTY_REPO)
            throw e
        } catch (e: Throwable) {
            updateProcess(processEntryId, ServerApiPort.PROCESS_STATUS_FAIL)
            throw e
        }
    }

    private fun postRepoFromServer(serverRepo: Repo) {
        val repo = serverApi.postRepo(serverRepo).getOrThrow()
        serverRepo.commits = repo.commits
        logger.info {
            "Received repo from server with ${serverRepo.commits.size} commits"
        }
    }

    private fun postAuthorsToServer(authors: HashSet<Author>,
                                    serverRepo: Repo) {
        authors.forEach { author -> author.repo = serverRepo }
        for (authorsBatch in authors.chunked(1000)) {
            serverApi.postAuthors(authorsBatch).onErrorThrow()
        }
    }

    private fun initServerRepo(localRepo: LocalRepo,
                               initCommitRehash: String,
                               processEntryId: Int?): Repo {
        val rehash = RehashCalculatorService.calculateRepoRehash(
            initCommitRehash, localRepo)
        return Repo(
            initialCommitRehash = initCommitRehash,
            rehash = rehash,
            meta = localRepo.meta,
            processEntryId = processEntryId ?: 0
        )
    }

    private fun filterEmails(emails: HashSet<String>,
                             serverRepo: Repo): HashSet<String> {
        val knownEmails = hashSetOf<String>()
        knownEmails.addAll(configurator.getUser().emails.map { it.email })
        knownEmails.addAll(serverRepo.emails)
        return knownEmails.filter { emails.contains(it) }.toHashSet()
    }

    private fun updateProcess(processEntryId: Int?, status: Int,
                              errorCode: Int = 0) {
        if (processEntryId == null) return
        val processEntry = ProcessEntry(id = processEntryId, status = status,
            errorCode = errorCode)
        serverApi.postProcess(listOf(processEntry)).onErrorThrow()
    }

    /**
     * Delete locally missing commits from server.
     */
    private fun deleteStaleCommits(serverRepo: Repo, rehashes: List<String>) {
        val serverHistoryRehashes = serverRepo.commits
            .map { commit -> commit.rehash }
            .toHashSet()
        val firstOverlapCommitRehash = rehashes.firstOrNull { rehash ->
            serverHistoryRehashes.contains(rehash)
        }
        val deletedCommits = serverRepo.commits
            .takeWhile { it.rehash != firstOverlapCommitRehash }
        if (deletedCommits.isNotEmpty()) {
            serverApi.deleteCommits(deletedCommits).onErrorThrow()
            logger.info { "Sent ${deletedCommits.size} deleted commits to server" }
        }
    }
}
