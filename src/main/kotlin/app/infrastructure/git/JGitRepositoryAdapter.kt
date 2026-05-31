package app.infrastructure.git

import app.application.ports.GitRepositoryPort
import app.application.ports.LoggerPort
import app.domain.entities.Author
import app.domain.entities.Commit
import app.domain.entities.LocalRepo
import app.domain.entities.Repo
import app.domain.errors.EmptyRepoException
import app.domain.valueobjects.DiffContent
import app.domain.valueobjects.DiffFile
import app.domain.valueobjects.DiffRange
import io.reactivex.Observable
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.LinkedList

/**
 * Adapter: Implements GitRepositoryPort using JGit library.
 * This is an output adapter — encapsulates all JGit operations
 * so the application layer never touches JGit directly.
 */
class JGitRepositoryAdapter(
    private val logger: LoggerPort
) : GitRepositoryPort {

    companion object {
        private val REMOTE_HEAD = "refs/remotes/origin/HEAD"
        private val REMOTE_MASTER_BRANCH = "refs/remotes/origin/master"
        private val LOCAL_MASTER_BRANCH = "refs/heads/master"
        private val LOCAL_HEAD = "HEAD"
        private val REFS = listOf(REMOTE_HEAD, REMOTE_MASTER_BRANCH,
            LOCAL_MASTER_BRANCH, LOCAL_HEAD)
        private val CONF_FILE_PATH = ".sourcerer-conf"
        private val MAX_DIFF_SIZE = 600000
        private val coauthoredRegex = Regex("""Co-authored-by: (.+) <(.+)>""")
    }

    override fun isValidRepo(path: String): Boolean {
        var git: Git? = null
        var repository: Repository? = null
        return try {
            git = Git.open(File(path))
            repository = git.repository
            getDefaultBranchHead(git) != null
        } catch (e: Exception) {
            false
        } finally {
            repository?.close()
            git?.close()
        }
    }

    override fun fetchRehashesAndAuthors(repoPath: String):
        Triple<List<String>, HashSet<Author>, HashMap<String, Int>> {

        val git = openGit(repoPath)
        try {
            val head = RevWalk(git.repository)
                .parseCommit(getDefaultBranchHead(git))

            val revWalk = RevWalk(git.repository)
            revWalk.markStart(head)

            val commitsRehashes = LinkedList<String>()
            val emails = hashSetOf<String>()
            val names = hashMapOf<String, String>()
            val commitsCount = hashMapOf<String, Int>()
            val coauthorsList = mutableListOf<Author>()

            var commit: RevCommit? = revWalk.next()
            while (commit != null) {
                commitsRehashes.add(DigestUtils.sha256Hex(commit.name))
                val email = commit.authorIdent.emailAddress.toLowerCase()
                val name = commit.authorIdent.name
                if (!emails.contains(email)) {
                    emails.add(email)
                    names[email] = name
                } else {
                    if (name.length > names[email]!!.length) {
                        names[email] = name
                    }
                }
                val coauthors = getCoauthors(commit.fullMessage)
                coauthorsList.addAll(coauthors)
                commitsCount[email] = commitsCount.getOrDefault(email, 0) + 1
                commit.disposeBody()
                commit = revWalk.next()
            }
            revWalk.dispose()

            val authors = emails.map { email -> Author(names[email]!!, email) }
                .toHashSet()
            authors.addAll(coauthorsList)

            return Triple(commitsRehashes, authors, commitsCount)
        } finally {
            git.repository?.close()
            git.close()
        }
    }

    override fun getCommitsObservable(repoPath: String,
                                      repo: Repo,
                                      filteredEmails: HashSet<String>?,
                                      extractCoauthors: Boolean): Observable<Commit> {
        return Observable.create { subscriber ->
            val git = openGit(repoPath)
            val jgitRepo = git.repository
            val revWalk = RevWalk(jgitRepo)
            val head = revWalk.parseCommit(getDefaultBranchHead(git))

            val df = DiffFormatter(DisabledOutputStream.INSTANCE)
            df.setRepository(jgitRepo)
            df.isDetectRenames = true

            val confTreeWalk = TreeWalk(jgitRepo)
            confTreeWalk.addTree(head.tree)
            confTreeWalk.setFilter(PathFilter.create(CONF_FILE_PATH))

            var ignoredPaths = if (confTreeWalk.next()) {
                getIgnoredPaths(jgitRepo, confTreeWalk.getObjectId(0))
            } else listOf()

            val totalCommitCount = repo.commits.size
            var commitCount = 0
            revWalk.markStart(head)
            var commit: RevCommit? = revWalk.next()

            while (commit != null) {
                commitCount++
                val parentCommit: RevCommit? = revWalk.next()

                val perc = if (totalCommitCount != 0) {
                    (commitCount.toDouble() / totalCommitCount) * 100
                } else 0.0
                logger.printCommit(commit.shortMessage, commit.name, perc)

                val email = commit.authorIdent.emailAddress.toLowerCase()
                if (filteredEmails != null && !filteredEmails.contains(email)) {
                    commit = parentCommit
                    continue
                }

                val diffEntries = df.scan(parentCommit, commit)
                    .filter { diff -> diff.changeType != DiffEntry.ChangeType.COPY }
                    .filter { diff ->
                        val path = diff.newPath
                        val fileId = if (path != DiffEntry.DEV_NULL) {
                            diff.newId.toObjectId()
                        } else {
                            diff.oldId.toObjectId()
                        }
                        val stream = try {
                            jgitRepo.open(fileId).openStream()
                        } catch (e: Exception) { null }
                        stream != null && !RawText.isBinary(stream)
                    }
                    .filter { diff ->
                        val filePath = if (diff.newPath != DiffEntry.DEV_NULL) {
                            diff.newPath
                        } else diff.oldPath

                        if (diff.oldPath == CONF_FILE_PATH) {
                            ignoredPaths = getIgnoredPaths(jgitRepo,
                                diff.newId.toObjectId())
                        }

                        !ignoredPaths.any { path ->
                            if (path.endsWith("/")) filePath.startsWith(path)
                            else path == filePath
                        }
                    }

                val diffFiles = diffEntries.mapNotNull { diff ->
                    val edits = df.toFileHeader(diff).toEditList()
                    if (edits.fold(0) { acc, edit ->
                            acc + edit.lengthA + edit.lengthB
                        } >= MAX_DIFF_SIZE) return@mapNotNull null

                    val newContent = getContentByObjectId(jgitRepo,
                        diff.newId.toObjectId())
                    val oldContent = getContentByObjectId(jgitRepo,
                        diff.oldId.toObjectId())

                    if (newContent != null && oldContent != null) {
                        val path = when (diff.changeType) {
                            DiffEntry.ChangeType.DELETE -> diff.oldPath
                            else -> diff.newPath
                        }
                        DiffFile(
                            path = path,
                            changeType = diff.changeType.name,
                            old = DiffContent(oldContent, edits.map { edit ->
                                DiffRange(edit.beginA, edit.endA)
                            }),
                            new = DiffContent(newContent, edits.map { edit ->
                                DiffRange(edit.beginB, edit.endB)
                            })
                        )
                    } else null
                }

                val coauthors = if (extractCoauthors) {
                    getCoauthors(commit.fullMessage)
                } else listOf()

                val domainCommit = Commit(
                    rehash = DigestUtils.sha256Hex(commit.id.name),
                    author = Author(commit.authorIdent.name,
                        commit.authorIdent.emailAddress.toLowerCase()),
                    dateTimestamp = commit.authorIdent.getWhen().time / 1000,
                    dateTimeZoneOffset = commit.authorIdent.timeZoneOffset,
                    treeRehash = DigestUtils.sha256Hex(commit.tree.name),
                    coauthors = coauthors,
                    repo = repo
                )
                domainCommit.diffs = diffFiles
                domainCommit.numLinesAdded = diffFiles.fold(0) { total, file ->
                    total + file.getAllAdded().size
                }
                domainCommit.numLinesDeleted = diffFiles.fold(0) { total, file ->
                    total + file.getAllDeleted().size
                }

                subscriber.onNext(domainCommit)
                commit = parentCommit
            }

            revWalk.dispose()
            df.close()
            git.repository?.close()
            git.close()
            subscriber.onComplete()
        }
    }

    override fun parseGitConfig(repoPath: String, localRepo: LocalRepo) {
        val git = openGit(repoPath)
        try {
            val config = git.repository.config
            localRepo.author = Author(
                name = config.getString("user", null, "name") ?: "",
                email = config.getString("user", null, "email") ?: ""
            )
            localRepo.remoteOrigin =
                config.getString("remote", "origin", "url") ?: ""
        } finally {
            git.repository?.close()
            git.close()
        }
    }

    private fun openGit(path: String): Git {
        return try {
            Git.open(File(path))
        } catch (e: IOException) {
            throw IllegalStateException("Cannot access repository at $path")
        }
    }

    private fun getDefaultBranchHead(git: Git): ObjectId? {
        for (ref in REFS) {
            val branch = git.repository.resolve(ref) ?: continue
            return branch
        }
        throw EmptyRepoException("No remote default, master or HEAD found")
    }

    private fun getContentByObjectId(repo: Repository,
                                     objectId: ObjectId): List<String>? {
        return try {
            val obj = repo.open(objectId)
            val rawText = RawText(obj.bytes)
            val content = ArrayList<String>(rawText.size())
            for (i in 0 until rawText.size()) {
                content.add(rawText.getString(i))
            }
            content
        } catch (e: Exception) {
            listOf()
        }
    }

    private fun getIgnoredPaths(repo: Repository,
                                objectId: ObjectId?): List<String> {
        return try {
            if (objectId == null) return listOf()
            val list = mutableListOf<String>()
            val fileLoader = repo.open(objectId)
            val reader = BufferedReader(InputStreamReader(
                fileLoader.openStream()))
            var collectIgnored = false
            for (line in reader.lines()) {
                if (line == "" || line.startsWith("#")) continue
                if (line.startsWith("[")) {
                    collectIgnored = (line == "[ignore]")
                    continue
                }
                if (collectIgnored) list.add(line)
            }
            list
        } catch (e: Exception) {
            listOf()
        }
    }

    private fun getCoauthors(message: String): List<Author> {
        val coauthorsResult = coauthoredRegex.findAll(message)
        return coauthorsResult.map { result ->
            Author(result.groupValues[1], result.groupValues[2].toLowerCase())
        }.toList()
    }
}
