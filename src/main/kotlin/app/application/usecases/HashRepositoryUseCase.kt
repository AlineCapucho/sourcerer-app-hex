package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.GitRepositoryPort
import app.application.ports.LoggerPort
import app.application.ports.ServerApiPort
import app.domain.entities.*
import app.domain.errors.EmptyRepoException
import app.domain.errors.HashingException
import app.domain.errors.InvalidRepoException
import app.domain.services.CommitStatsExtractorService
import app.domain.services.LanguageDetectionService
import app.domain.services.RehashCalculatorService
import app.domain.valueobjects.ProcessEntry
import java.util.concurrent.TimeUnit

/**
 * Use case: Hash a local repository and upload stats to the server.
 *
 * This is the main orchestration use case. It coordinates:
 * - Validating the repository
 * - Crawling commit history
 * - Extracting statistics per commit
 * - Sending results to the server API
 */
class HashRepositoryUseCase(
    private val serverApi: ServerApiPort,
    private val gitRepository: GitRepositoryPort,
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort,
    private val languageDetector: LanguageDetectionService
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

            // Hash commits: extract stats and send to server.
            val knownCommits = serverRepo.commits.toHashSet()
            observable
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
}
