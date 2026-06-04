package app.infrastructure.hashers

import app.domain.entities.Author
import app.domain.entities.Commit
import app.domain.entities.Fact
import app.domain.entities.Repo
import app.domain.services.FactCodes
import app.domain.services.FactHashingService
import app.infrastructure.extractors.Extractor
import io.reactivex.Observable
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Adapter: Implements FactHashingService.
 *
 * Processes commit history to compute per-author statistical facts including:
 * - Day of week / time of day distributions
 * - Repository date range (start/end)
 * - Commit count and average lines per commit
 * - Line length averages and total line count
 * - Lines-per-commit histogram (COMMIT_NUM_TO_LINE_NUM)
 * - Variable naming convention (snake_case, camelCase, other)
 * - Indentation style (tabs vs spaces)
 */
class FactHasherAdapter : FactHashingService {

    private val fsDayWeek = hashMapOf<String, Array<Int>>()
    private val fsDayTime = hashMapOf<String, Array<Int>>()
    private val fsRepoDateStart = hashMapOf<String, Long>()
    private val fsRepoDateEnd = hashMapOf<String, Long>()
    private val fsCommitLineNumAvg = hashMapOf<String, Double>()
    private val fsCommitNum = hashMapOf<String, Int>()
    private val fsLineLenAvg = hashMapOf<String, Double>()
    private val fsLineNum = hashMapOf<String, Long>()
    private val fsLinesPerCommits = hashMapOf<String, Array<Int>>()
    private val fsVariableNaming = hashMapOf<String, Array<Int>>()
    private val fsIndentation = hashMapOf<String, Array<Int>>()

    private val varNamingRegex = Regex("[a-z][A-Z]")
    private val stringRegex = Regex("""(".+?"|'.+?')""")

    private var serverRepo = Repo()
    private var computedFacts: List<Fact> = listOf()

    override fun updateFromObservable(observable: Observable<Commit>,
                                      repoRehash: String,
                                      rehashes: List<String>,
                                      emails: HashSet<String>,
                                      onError: (Throwable) -> Unit) {
        serverRepo = Repo(rehash = repoRehash)

        // Initialize accumulators for each author email.
        for (author in emails) {
            fsDayWeek[author] = Array(7) { 0 }
            fsDayTime[author] = Array(24) { 0 }
            fsRepoDateStart[author] = -1
            fsRepoDateEnd[author] = -1
            fsCommitLineNumAvg[author] = 0.0
            fsCommitNum[author] = 0
            fsLineLenAvg[author] = 0.0
            fsLineNum[author] = 0
            fsLinesPerCommits[author] = Array(rehashes.size) { 0 }
            fsVariableNaming[author] = Array(3) { 0 }
            fsIndentation[author] = Array(2) { 0 }
        }

        observable
            .filter { commit -> emails.contains(commit.author.email) }
            .subscribe({ commit -> processCommit(commit) }, onError, {
                try {
                    computedFacts = createFacts()
                } catch (e: Throwable) {
                    onError(e)
                }
            })
    }

    override fun getComputedFacts(): List<Fact> {
        return computedFacts
    }

    private fun processCommit(commit: Commit) {
        val email = commit.author.email
        val timestamp = commit.dateTimestamp
        val dateTime = LocalDateTime.ofEpochSecond(timestamp, 0,
            ZoneOffset.ofTotalSeconds(commit.dateTimeZoneOffset * 60))

        // DayWeek.
        val factDayWeek = fsDayWeek[email] ?: Array(7) { 0 }
        // The value is numbered from 1 (Monday) to 7 (Sunday).
        factDayWeek[dateTime.dayOfWeek.value - 1] += 1
        fsDayWeek[email] = factDayWeek

        // DayTime.
        val factDayTime = fsDayTime[email] ?: Array(24) { 0 }
        // Hour from 0 to 23.
        factDayTime[dateTime.hour] += 1
        fsDayTime[email] = factDayTime

        // RepoDateStart.
        fsRepoDateStart[email] = timestamp

        // RepoDateEnd.
        if (fsRepoDateEnd[email]!! == -1L) {
            fsRepoDateEnd[email] = timestamp
        }

        // Commits.
        val numCommits = fsCommitNum[email]!! + 1
        val numLinesCurrent = commit.numLinesAdded + commit.numLinesDeleted

        fsCommitNum[email] = numCommits
        fsCommitLineNumAvg[email] = calcIncAvg(fsCommitLineNumAvg[email]!!,
            numLinesCurrent.toDouble(), numCommits.toLong())

        val lines = commit.getAllAdded() + commit.getAllDeleted()
        lines.forEachIndexed { index, line ->
            fsLineLenAvg[email] = calcIncAvg(fsLineLenAvg[email]!!,
                line.length.toDouble(), fsLineNum[email]!! + index + 1)
        }
        fsLineNum[email] = fsLineNum[email]!! + lines.size

        fsLinesPerCommits[email]!![numCommits - 1] += lines.size

        // Variable naming.
        lines.forEach { line ->
            val tokens = tokenize(line)
            val underscores = tokens.count { it.contains('_') }
            val camelCases = tokens.count {
                !it.contains('_') && it.contains(varNamingRegex)
            }
            val others = tokens.size - underscores - camelCases
            fsVariableNaming[email]!![FactCodes.VARIABLE_NAMING_SNAKE_CASE] +=
                underscores
            fsVariableNaming[email]!![FactCodes.VARIABLE_NAMING_CAMEL_CASE] +=
                camelCases
            fsVariableNaming[email]!![FactCodes.VARIABLE_NAMING_OTHER] +=
                others
        }

        // Indentation.
        fsIndentation[email]!![FactCodes.INDENTATION_SPACES] +=
            lines.count { it.isNotBlank() && it.startsWith(" ") &&
                !it.contains("\t") }
        fsIndentation[email]!![FactCodes.INDENTATION_TABS] +=
            lines.count { it.startsWith("\t") }
    }

    private fun createFacts(): List<Fact> {
        val fs = mutableListOf<Fact>()
        val emails = fsDayWeek.keys

        emails.forEach { email ->
            val author = Author(email = email)
            fsDayTime[email]?.forEachIndexed { hour, count -> if (count > 0) {
                fs.add(Fact(serverRepo, FactCodes.COMMIT_DAY_TIME, hour,
                            count.toString(), author))
            }}
            fsDayWeek[email]?.forEachIndexed { day, count -> if (count > 0) {
                fs.add(Fact(serverRepo, FactCodes.COMMIT_DAY_WEEK, day,
                            count.toString(), author))
            }}
            fsVariableNaming[email]?.forEachIndexed { naming, count ->
                if (count > 0) {
                    fs.add(Fact(serverRepo, FactCodes.VARIABLE_NAMING, naming,
                            count.toString(), author))
                }
            }
            fsIndentation[email]?.forEachIndexed { indentation, count ->
                if (count > 0) {
                    fs.add(Fact(serverRepo, FactCodes.INDENTATION, indentation,
                            count.toString(), author))
                }
            }

            fs.add(Fact(serverRepo, FactCodes.REPO_DATE_START, 0,
                        fsRepoDateStart[email].toString(), author))
            fs.add(Fact(serverRepo, FactCodes.REPO_DATE_END, 0,
                        fsRepoDateEnd[email].toString(), author))
            fs.add(Fact(serverRepo, FactCodes.COMMIT_NUM, 0,
                        fsCommitNum[email].toString(), author))
            fs.add(Fact(serverRepo, FactCodes.COMMIT_LINE_NUM_AVG, 0,
                        fsCommitLineNumAvg[email].toString(), author))
            fs.add(Fact(serverRepo, FactCodes.LINE_NUM, 0,
                        fsLineNum[email].toString(), author))
            fs.add(Fact(serverRepo, FactCodes.LINE_LEN_AVG, 0,
                        fsLineLenAvg[email].toString(), author))
            val linesPerCommits = fsLinesPerCommits[email]!!
                .sliceArray(IntRange(0, fsCommitNum[email]!! - 1))
            addCommitsPerLinesFacts(fs, linesPerCommits, author)
        }
        return fs
    }

    /**
     * Tokenizes a line of code into identifier tokens for variable naming
     * analysis. Uses the same logic as the original Extractor.tokenize.
     */
    private fun tokenize(line: String): List<String> {
        val newLine = stringRegex.replace(line, "")
        return newLine.split(' ', '[', ',', ';', '*', '\n', ')', '(',
            '[', ']', '}', '{', '+', '-', '=', '&', '$', '!', '.', '>',
            '<', '#', '@', ':', '?', ']')
            .filter {
                it.isNotBlank() && !it.contains('"') && !it.contains('\'') &&
                it != "-" && it != "@"
            }
    }

    /**
     * Computes the average of a numerical sequence incrementally.
     * No overflow due to summing of elements.
     */
    private fun calcIncAvg(prev: Double, element: Double,
                           count: Long): Double {
        return prev * (1 - 1.0 / count) + element / count
    }

    /**
     * Computes binned histogram of lines per commit for the
     * COMMIT_NUM_TO_LINE_NUM fact.
     */
    private fun addCommitsPerLinesFacts(fs: MutableList<Fact>,
                                        linesPerCommits: Array<Int>,
                                        author: Author) {
        if (linesPerCommits.isEmpty()) return

        var max = linesPerCommits[0]
        var min = linesPerCommits[0]
        for (lines in linesPerCommits) {
            if (lines > max) {
                max = lines
            }
            if (lines < min) {
                min = lines
            }
        }

        val numBins = Math.min(10, max - min + 1)
        val binSize = (max - min + 1) / numBins.toDouble()
        val bins = Array(numBins) { 0 }
        for (numLines in linesPerCommits) {
            if (numLines == 0) {
                continue
            }

            val binId = Math.floor((numLines - min) / binSize).toInt()
            bins[binId]++
        }

        for ((binId, numCommits) in bins.withIndex()) {
            if (numCommits == 0) {
                continue
            }

            val numLines = Math.floor(min + binId * binSize).toInt()
            fs.add(Fact(serverRepo, FactCodes.COMMIT_NUM_TO_LINE_NUM,
                numLines, numCommits.toString(), author))
        }
    }
}
