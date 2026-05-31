package test.tests.utils

import app.domain.valueobjects.DiffFile
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals

/**
 * Tests for file utility functions.
 * Validates the same functionality as the original FileHelperTest:
 * - File extension extraction
 * - Specific extension handling (.min.js)
 */
class FileHelperTest : Spek({
    given("file extension extraction") {
        it("extracts simple extensions") {
            assertEquals("kt", DiffFile.getFileExtension("src/Main.kt"))
            assertEquals("java", DiffFile.getFileExtension("App.java"))
            assertEquals("py", DiffFile.getFileExtension("script.py"))
            assertEquals("js", DiffFile.getFileExtension("app.js"))
            assertEquals("ts", DiffFile.getFileExtension("index.ts"))
        }

        it("handles nested paths") {
            assertEquals("kt", DiffFile.getFileExtension("src/main/kotlin/App.kt"))
            assertEquals("java", DiffFile.getFileExtension("com/example/Main.java"))
        }

        it("handles specific extensions like .min.js") {
            assertEquals(".min.js", DiffFile.getFileExtension("bundle.min.js"))
            assertEquals(".min.js", DiffFile.getFileExtension("vendor.min.js"))
        }

        it("handles files without extension") {
            assertEquals("", DiffFile.getFileExtension("Makefile"))
            assertEquals("", DiffFile.getFileExtension("Dockerfile"))
        }

        it("handles dotfiles") {
            assertEquals("gitignore", DiffFile.getFileExtension(".gitignore"))
            assertEquals("yml", DiffFile.getFileExtension(".travis.yml"))
        }
    }
})
