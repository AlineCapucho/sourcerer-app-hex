package test.tests.hashers

import app.domain.services.CommitStatsExtractorService
import app.domain.services.LanguageDetectionService
import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffContent
import app.domain.valueobjects.DiffFile
import app.domain.valueobjects.DiffRange
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for CommitStatsExtractorService (domain service).
 * Validates:
 * - Extraction of stats from multiple files
 * - Filtering of restricted extensions
 * - Delegation to language detector
 */
class CommitStatsExtractorTest : Spek({

    val mockDetector = object : LanguageDetectionService {
        override fun detectAndAnalyze(file: DiffFile): List<CommitStats>? {
            val lang = when (file.extension) {
                "kt" -> "kotlin"
                "py" -> "python"
                "js" -> "javascript"
                else -> return null
            }
            val added = file.getAllAdded().size
            val deleted = file.getAllDeleted().size
            if (added == 0 && deleted == 0) return null
            return listOf(CommitStats(
                numLinesAdded = added,
                numLinesDeleted = deleted,
                type = 1,
                tech = lang
            ))
        }
    }

    given("extract from multiple files") {
        it("aggregates stats from all recognized files") {
            val files = listOf(
                createDiffFile("Main.kt", listOf("line1", "line2")),
                createDiffFile("script.py", listOf("x = 1")),
                createDiffFile("unknown.xyz", listOf("data"))
            )

            val stats = CommitStatsExtractorService.extract(files, mockDetector)

            assertEquals(2, stats.size)
            assertTrue(stats.any { it.tech == "kotlin" && it.numLinesAdded == 2 })
            assertTrue(stats.any { it.tech == "python" && it.numLinesAdded == 1 })
        }
    }

    given("restricted extensions filtering") {
        it("filters out .min.js files") {
            val files = listOf(
                createDiffFile("app.js", listOf("const x = 1;")),
                createDiffFile("bundle.min.js", listOf("minified"))
            )

            val stats = CommitStatsExtractorService.extract(files, mockDetector)

            assertEquals(1, stats.size)
            assertEquals("javascript", stats.first().tech)
        }
    }

    given("empty diffs") {
        it("returns empty list for files with no changes") {
            val files = listOf(
                DiffFile(
                    path = "empty.kt",
                    changeType = "MODIFY",
                    old = DiffContent(listOf(), listOf()),
                    new = DiffContent(listOf(), listOf())
                )
            )

            val stats = CommitStatsExtractorService.extract(files, mockDetector)
            assertEquals(0, stats.size)
        }
    }
})

private fun createDiffFile(path: String, lines: List<String>): DiffFile {
    return DiffFile(
        path = path,
        changeType = "ADD",
        old = DiffContent(listOf(), listOf()),
        new = DiffContent(lines, listOf(DiffRange(0, lines.size)))
    )
}
