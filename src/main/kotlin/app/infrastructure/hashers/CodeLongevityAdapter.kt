package app.infrastructure.hashers

import app.application.ports.LoggerPort
import app.domain.entities.Author
import app.domain.entities.Fact
import app.domain.entities.Repo
import app.domain.services.CodeLongevityService
import app.domain.services.FactCodes
import io.reactivex.Observable
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Represents a code line in a file revision.
 */
class RevCommitLine(val commit: RevCommit, val fileId: AnyObjectId,
                    val file: String, val line: Int,
                    val isDeleted: Boolean) {

    val id: String
        get() = "${fileId.name}:$line"
}

/**
 * Represents a code line's journey through history (from revision to revision).
 */
class CodeLine(val repo: Repository,
               val from: RevCommitLine, val to: RevCommitLine) {

    val oldId: String
        get() = from.id

    val newId: String
        get() = to.id

    var age: Long = 0
        get() {
            if (field == 0L) {
                field = (to.commit.commitTime - from.commit.commitTime).toLong()
            }
            return field
        }

    val authorEmail: String
        get() = from.commit.authorIdent.emailAddress

    val editorEmail: String?
        get() = if (isDeleted) to.commit.authorIdent.emailAddress else null

    val editDate: Date
        get() = Date(to.commit.commitTime.toLong() * 1000)

    val isDeleted: Boolean
        get() = to.isDeleted
}

/**
 * Stores accumulated ages and lasting lines for serialization between runs.
 */
class CodeLineAges : Serializable, Cloneable {

    data class AggrAge(var sum: Long = 0L, var count: Int = 0) : Serializable

    data class LineInfo(var age: Long, var email: String) : Serializable

    /** Aggregated code line ages for user emails, collected from deleted lines. */
    var aggrAges: HashMap<String, AggrAge> = hashMapOf()

    /** Map of existing code line ids to their ages at the revision. */
    var lastingLines: HashMap<String, LineInfo> = hashMapOf()

    public override fun clone(): CodeLineAges {
        val clone = CodeLineAges()
        aggrAges.forEach { (email, age) ->
            clone.aggrAges[email] = age.copy()
        }
        lastingLines.forEach { (id, line) ->
            clone.lastingLines[id] = line.copy()
        }
        return clone
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Tracks editing patterns between authors for COLLEAGUES fact generation.
 * Detects pairs of authors who edit each other's code within monthly windows.
 */
class Colleagues(private val serverRepo: Repo) {

    // Map of (author_email, editor_email) -> (month -> min_vicinity_age).
    private val map: HashMap<Pair<String, String>,
                             HashMap<String, Long>> = hashMapOf()

    fun collect(authorEmail: String, editorEmail: String?,
                editDate: Date, age: Long) {
        if (editorEmail == null || authorEmail == editorEmail) {
            return
        }
        val emails = Pair(authorEmail, editorEmail)
        val dates = map.getOrPut(emails) { hashMapOf() }
        val month = SimpleDateFormat("yyyy-MM").format(editDate)

        val vicinity = dates.getOrPut(month) { age }
        if (vicinity > age) {
            dates[month] = age
        }
    }

    fun calculateFacts(): List<Fact> {
        val facts = mutableListOf<Fact>()
        val auxHash = hashSetOf<Pair<String, String>>()

        for ((pair, dates) in map) {
            val email1 = pair.first
            val email2 = pair.second
            if (auxHash.contains(Pair(email2, email1))) {
                continue
            }

            val min1 = dates.minByOrNull { (_, vicinity) -> vicinity }
                ?: continue
            val dates2 = map[Pair(email2, email1)]
            if (dates2 != null) {
                auxHash.add(Pair(email1, email2))

                val min2 = dates2.minByOrNull { (_, vicinity) -> vicinity }
                    ?: continue
                val min: Long = if (min1.value < min2.value) {
                    min1.value
                } else {
                    min2.value
                }

                facts.add(Fact(
                    repo = serverRepo,
                    code = FactCodes.COLLEAGUES,
                    value = email1,
                    value2 = email2,
                    value3 = min.toString()
                ))
            }
        }
        return facts
    }
}

/**
 * JGit diff data carrying a commit and its associated diffs with edit lists.
 */
data class JgitLongevityData(var commit: RevCommit? = null,
                             var diffs: List<JgitLongevityDiff>? = null)

data class JgitLongevityDiff(val diffEntry: DiffEntry, val editList: EditList)

/**
 * Adapter: Implements CodeLongevityService.
 *
 * Computes code line longevity by walking the git history backwards and
 * tracking how long each code line survives. Produces:
 * - LINE_LONGEVITY: average code line age per author
 * - LINE_LONGEVITY_REPO: average code line age for the whole repository
 * - COLLEAGUES: pairs of authors who edit each other's code
 */
class CodeLongevityAdapter(
    private val logger: LoggerPort
) : CodeLongevityService {

    companion object {
        private val REMOTE_HEAD = "refs/remotes/origin/HEAD"
        private val REMOTE_MASTER_BRANCH = "refs/remotes/origin/master"
        private val LOCAL_MASTER_BRANCH = "refs/heads/master"
        private val LOCAL_HEAD = "HEAD"
        private val REFS = listOf(REMOTE_HEAD, REMOTE_MASTER_BRANCH,
            LOCAL_MASTER_BRANCH, LOCAL_HEAD)
    }

    override fun calculateLongevityFacts(repoPath: String, repoRehash: String,
                                         emails: HashSet<String>): List<Fact> {
        val git = Git.open(File(repoPath))
        try {
            val repo = git.repository
            val revWalk = RevWalk(repo)
            val head = revWalk.parseCommit(getDefaultBranchHead(git))
            val serverRepo = Repo(rehash = repoRehash)
            val colleagues = Colleagues(serverRepo)
            val dataPath = getDataPath(repoRehash)

            var storedHead: RevCommit? = null
            var ageData = CodeLineAges()

            // Load existing age data if available.
            try {
                val file = dataPath.toFile()
                val iStream = ObjectInputStream(FileInputStream(file))
                val storedHeadId = iStream.readUTF()
                logger.debug { "Stored repo head: $storedHeadId" }
                storedHead = revWalk.parseCommit(repo.resolve(storedHeadId))
                if (storedHead == head) {
                    // Already computed, return empty — caller should use saved.
                    return listOf()
                }
                ageData = (iStream.readObject() ?: CodeLineAges())
                    as CodeLineAges
            } catch (e: FileNotFoundException) {
                // No stored data, compute from scratch.
            } catch (e: Exception) {
                logger.error(e, "Failed to read longevity data. " +
                    "CAUTION: data will be recomputed.")
            }

            // Build diff observable.
            val diffObservable = getDiffObservable(git, head)

            // Get lines observable and process ages.
            getLinesObservable(repo, head, storedHead, diffObservable)
                .blockingSubscribe({ line ->
                    if (line.isDeleted) {
                        if (ageData.lastingLines.contains(line.oldId)) {
                            line.age += ageData.lastingLines
                                .remove(line.oldId)!!.age
                        }
                        val aggrAge = ageData.aggrAges.getOrPut(
                            line.authorEmail) { CodeLineAges.AggrAge() }
                        aggrAge.sum += line.age
                        aggrAge.count += 1

                        colleagues.collect(line.authorEmail, line.editorEmail,
                            line.editDate, line.age)
                    } else {
                        var age = line.age
                        if (ageData.lastingLines.contains(line.oldId)) {
                            age += ageData.lastingLines
                                .remove(line.oldId)!!.age
                        }
                        ageData.lastingLines[line.newId] =
                            CodeLineAges.LineInfo(age, line.authorEmail)
                    }
                }, { e -> logger.error(e, "Error processing longevity line") })

            // Store ages for subsequent runs.
            try {
                val file = dataPath.toFile()
                file.parentFile?.mkdirs()
                val oStream = ObjectOutputStream(FileOutputStream(file))
                oStream.writeUTF(head.name)
                oStream.writeObject(ageData)
            } catch (e: Exception) {
                logger.error(e, "Failed to save longevity data. " +
                    "CAUTION: data will be recomputed on a next run.")
            }

            // Calculate and return facts.
            val facts = calculateFacts(ageData, serverRepo, emails)
            val colleagueFacts = colleagues.calculateFacts()
            return facts + colleagueFacts
        } finally {
            git.repository?.close()
            git.close()
        }
    }

    /**
     * Computes LINE_LONGEVITY and LINE_LONGEVITY_REPO facts from age data.
     */
    private fun calculateFacts(ages: CodeLineAges, serverRepo: Repo,
                               emails: HashSet<String>): List<Fact> {
        var repoTotal = 0
        var repoSum: Long = 0
        val aggrAges: HashMap<String, CodeLineAges.AggrAge> = hashMapOf()

        ages.aggrAges.forEach { (email, aggrAge) ->
            repoSum += aggrAge.sum
            repoTotal += aggrAge.count
            if (emails.contains(email)) {
                aggrAges[email] = aggrAge
            }
        }

        ages.lastingLines.forEach { (_, info) ->
            val aggrAge = aggrAges.getOrPut(info.email) {
                CodeLineAges.AggrAge()
            }
            aggrAge.sum += info.age
            aggrAge.count += 1

            repoSum += info.age
            repoTotal += 1
        }

        val repoAvg = if (repoTotal > 0) { repoSum / repoTotal } else 0
        val facts = mutableListOf<Fact>()
        facts.add(Fact(
            repo = serverRepo,
            code = FactCodes.LINE_LONGEVITY_REPO,
            value = repoAvg.toString()
        ))

        val secondsInDay = 86400
        val repoAvgDays = repoAvg / secondsInDay
        logger.info { "Repo average code line age is $repoAvgDays days, " +
            "lines total: $repoTotal" }

        for (email in emails) {
            val aggrAge = aggrAges[email] ?: CodeLineAges.AggrAge()
            val avg = if (aggrAge.count > 0) { aggrAge.sum / aggrAge.count }
                      else 0
            facts.add(Fact(
                repo = serverRepo,
                code = FactCodes.LINE_LONGEVITY,
                value = avg.toString(),
                author = Author(email = email)
            ))
        }

        return facts
    }

    /**
     * Returns an observable of diffs from HEAD backwards through history.
     */
    private fun getDiffObservable(git: Git, head: RevCommit):
        Observable<JgitLongevityData> = Observable.create { subscriber ->

        val repo = git.repository
        val revWalk = RevWalk(repo)
        val df = DiffFormatter(DisabledOutputStream.INSTANCE)
        df.setRepository(repo)
        df.isDetectRenames = true

        revWalk.markStart(head)
        var commit: RevCommit? = revWalk.next()

        while (commit != null) {
            val parentCommit: RevCommit? = revWalk.next()

            val diffEntries = df.scan(parentCommit, commit)
                .filter { diff ->
                    diff.changeType != DiffEntry.ChangeType.COPY
                }
                .filter { diff ->
                    val fileId = if (diff.newPath != DiffEntry.DEV_NULL) {
                        diff.newId.toObjectId()
                    } else {
                        diff.oldId.toObjectId()
                    }
                    val stream = try {
                        repo.open(fileId).openStream()
                    } catch (e: Exception) { null }
                    stream != null && !RawText.isBinary(stream)
                }

            val diffs = diffEntries.map { diff ->
                JgitLongevityDiff(diff, df.toFileHeader(diff).toEditList())
            }

            subscriber.onNext(JgitLongevityData(commit, diffs))
            commit = parentCommit
        }

        df.close()
        revWalk.dispose()
        subscriber.onComplete()
    }

    /**
     * Returns an observable of CodeLine objects representing each line's
     * journey through history. Walks backwards from HEAD applying diffs
     * to track insertions and deletions.
     */
    private fun getLinesObservable(repo: Repository, head: RevCommit,
                                  tail: RevCommit?,
                                  diffObservable: Observable<JgitLongevityData>):
        Observable<CodeLine> = Observable.create { subscriber ->

        val headWalk = TreeWalk(repo)
        headWalk.isRecursive = true
        headWalk.addTree(head.tree)

        val files: MutableMap<String, ArrayList<RevCommitLine>> = mutableMapOf()

        // Build a map of file names and their code lines at HEAD.
        while (headWalk.next()) {
            try {
                val path = headWalk.pathString
                val fileId = headWalk.getObjectId(0)
                val fileLoader = repo.open(fileId)
                if (!RawText.isBinary(fileLoader.openStream())) {
                    val fileText = RawText(fileLoader.bytes)
                    val lines = ArrayList<RevCommitLine>(fileText.size())
                    for (idx in 0 until fileText.size()) {
                        lines.add(RevCommitLine(head, fileId, path, idx, false))
                    }
                    files[path] = lines
                }
            } catch (e: Exception) {
                // Skip files that cannot be read.
            }
        }

        diffObservable
            .takeWhile { (commit, _) -> commit != tail }
            .subscribe({ (commit, diffs) ->
                // Walk diffs in reverse to handle double renames properly.
                for ((diff, editList) in diffs!!.asReversed()) {
                    val oldPath = diff.oldPath
                    val oldId = diff.oldId.toObjectId()
                    val newPath = diff.newPath
                    val newId = diff.newId.toObjectId()

                    // File was deleted: initialize the line array.
                    if (diff.changeType == DiffEntry.ChangeType.DELETE) {
                        val fileLoader = repo.open(oldId)
                        val fileText = RawText(fileLoader.bytes)
                        files[oldPath] = ArrayList(fileText.size())
                    }

                    // If file was deleted, new path is /dev/null.
                    val path = if (newPath != DiffEntry.DEV_NULL) {
                        newPath
                    } else {
                        oldPath
                    }
                    val lines = files[path] ?: continue

                    // Process insertions in REVERSE order to keep indices in
                    // sync with the lines array.
                    for (edit in editList.asReversed()) {
                        val insCount = edit.lengthB
                        if (insCount > 0) {
                            val insStart = edit.beginB
                            val insEnd = edit.endB

                            for (idx in insStart until insEnd) {
                                val from = RevCommitLine(commit!!, newId,
                                    newPath, idx, false)
                                try {
                                    val to = lines[idx]
                                    val cl = CodeLine(repo, from, to)
                                    subscriber.onNext(cl)
                                } catch (e: IndexOutOfBoundsException) {
                                    logger.error(e, "No line at $idx; " +
                                        "commit: ${commit.name}; " +
                                        "'${commit.shortMessage}'")
                                    throw e
                                }
                            }
                            lines.subList(insStart, insEnd).clear()
                        }
                    }

                    // Process deletions in FORWARD order.
                    for (edit in editList) {
                        val delCount = edit.lengthA
                        if (delCount > 0) {
                            val delStart = edit.beginA
                            val delEnd = edit.endA

                            val tmpLines = ArrayList<RevCommitLine>(delCount)
                            for (idx in delStart until delEnd) {
                                tmpLines.add(RevCommitLine(commit!!, oldId,
                                    oldPath, idx, true))
                            }
                            lines.addAll(delStart, tmpLines)
                        }
                    }

                    // File was renamed: update the files map.
                    if (diff.changeType == DiffEntry.ChangeType.RENAME) {
                        files[oldPath] = files.remove(newPath)!!
                    }
                }
            }, { e ->
                logger.error(e, "Error in diff processing")
                subscriber.onError(e)
            }, {
                // Emit remaining lines if a tail revision was provided.
                if (tail != null) {
                    val tailWalk = TreeWalk(repo)
                    tailWalk.isRecursive = true
                    tailWalk.addTree(tail.tree)

                    while (tailWalk.next()) {
                        val filePath = tailWalk.pathString
                        val lines = files[filePath]
                        if (lines != null) {
                            val fileId = tailWalk.getObjectId(0)
                            for (idx in 0 until lines.size) {
                                val from = RevCommitLine(tail, fileId,
                                    filePath, idx, false)
                                val cl = CodeLine(repo, from, lines[idx])
                                subscriber.onNext(cl)
                            }
                        }
                    }
                }
                subscriber.onComplete()
            })
    }

    /**
     * Resolves the HEAD object id from known branch references.
     */
    private fun getDefaultBranchHead(git: Git): ObjectId {
        for (ref in REFS) {
            val branch = git.repository.resolve(ref) ?: continue
            return branch
        }
        throw IllegalStateException("No remote default, master or HEAD found")
    }

    /**
     * Returns the storage path for longevity data persistence.
     */
    private fun getDataPath(repoRehash: String): Path {
        val basePath = Paths.get(System.getProperty("user.dir"), "data",
            "longevity")
        if (Files.notExists(basePath)) {
            Files.createDirectories(basePath)
        }
        return basePath.resolve(repoRehash)
    }
}
