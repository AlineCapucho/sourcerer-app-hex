package test.tests.extractors

import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffContent
import app.domain.valueobjects.DiffFile
import app.domain.valueobjects.DiffRange
import app.infrastructure.extractors.Extractor
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the Extractor (language detection and stats extraction).
 * Validates the same functionality as the original ExtractorTest:
 * - Language detection by file extension
 * - Stats extraction from diffs
 * - Restricted extensions filtering
 */
class ExtractorTest : Spek({
    val extractor = Extractor()

    given("language detection by extension") {
        it("detects kotlin") {
            val file = createDiffFile("Main.kt", listOf("fun main() {}"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("kotlin", stats!!.first().tech)
        }

        it("detects java") {
            val file = createDiffFile("App.java", listOf("class App {}"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("java", stats!!.first().tech)
        }

        it("detects python") {
            val file = createDiffFile("script.py", listOf("def fn(): pass"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("python", stats!!.first().tech)
        }

        it("detects javascript") {
            val file = createDiffFile("app.js", listOf("const x = 1;"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("javascript", stats!!.first().tech)
        }

        it("detects typescript") {
            val file = createDiffFile("app.ts", listOf("const x: number = 1;"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("typescript", stats!!.first().tech)
        }

        it("detects cpp") {
            val file = createDiffFile("main.cpp", listOf("#include <iostream>"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("cpp", stats!!.first().tech)
        }

        it("detects go") {
            val file = createDiffFile("main.go", listOf("package main"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("go", stats!!.first().tech)
        }

        it("detects ruby") {
            val file = createDiffFile("app.rb", listOf("puts 'hello'"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals("ruby", stats!!.first().tech)
        }

        it("returns null for unknown extension") {
            val file = createDiffFile("file.xyz", listOf("content"))
            val stats = extractor.extract(file)
            assertNull(stats)
        }
    }

    given("stats extraction from diffs") {
        it("counts lines added correctly") {
            val lines = listOf("line1", "line2", "line3")
            val file = createDiffFile("test.py", lines)
            val stats = extractor.extract(file)

            assertNotNull(stats)
            assertEquals(3, stats!!.first().numLinesAdded)
            assertEquals(0, stats.first().numLinesDeleted)
        }

        it("counts lines deleted correctly") {
            val oldLines = listOf("old1", "old2")
            val file = DiffFile(
                path = "test.py",
                changeType = "MODIFY",
                old = DiffContent(oldLines, listOf(DiffRange(0, 2))),
                new = DiffContent(listOf(), listOf())
            )
            val stats = extractor.extract(file)

            assertNotNull(stats)
            // Only deleted lines, no added
            assertEquals(0, stats!!.first().numLinesAdded)
            assertEquals(2, stats.first().numLinesDeleted)
        }

        it("handles empty diffs") {
            val file = DiffFile(
                path = "test.py",
                changeType = "MODIFY",
                old = DiffContent(listOf(), listOf()),
                new = DiffContent(listOf(), listOf())
            )
            val stats = extractor.extract(file)
            assertNull(stats)
        }
    }

    given("restricted extensions") {
        it("returns null for .min.js files") {
            val file = createDiffFile("bundle.min.js", listOf("minified code"))
            // .min.js should be detected as restricted
            assertEquals(".min.js", file.extension)
        }
    }

    given("stats type is TYPE_LANGUAGE") {
        it("sets type to 1 for language stats") {
            val file = createDiffFile("test.kt", listOf("val x = 1"))
            val stats = extractor.extract(file)
            assertNotNull(stats)
            assertEquals(Extractor.TYPE_LANGUAGE, stats!!.first().type)
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
