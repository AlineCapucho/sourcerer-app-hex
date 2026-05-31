package test.tests.hashers

import app.domain.entities.LocalRepo
import app.domain.services.RehashCalculatorService
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for RehashCalculatorService (domain service).
 * Validates:
 * - Deterministic rehash calculation
 * - Different repos produce different rehashes
 * - Remote origin vs local path fallback
 */
class RehashCalculatorTest : Spek({
    given("rehash calculation") {
        it("produces deterministic results") {
            val localRepo = LocalRepo("/path/to/repo")
            localRepo.remoteOrigin = "https://github.com/user/repo.git"

            val rehash1 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo)
            val rehash2 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo)

            assertEquals(rehash1, rehash2)
        }

        it("different initial commits produce different rehashes") {
            val localRepo = LocalRepo("/path/to/repo")
            localRepo.remoteOrigin = "https://github.com/user/repo.git"

            val rehash1 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo)
            val rehash2 = RehashCalculatorService.calculateRepoRehash(
                "def456", localRepo)

            assertNotEquals(rehash1, rehash2)
        }

        it("different remotes produce different rehashes") {
            val localRepo1 = LocalRepo("/path/to/repo")
            localRepo1.remoteOrigin = "https://github.com/user/repo1.git"

            val localRepo2 = LocalRepo("/path/to/repo")
            localRepo2.remoteOrigin = "https://github.com/user/repo2.git"

            val rehash1 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo1)
            val rehash2 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo2)

            assertNotEquals(rehash1, rehash2)
        }

        it("uses local path when no remote origin") {
            val localRepo1 = LocalRepo("/path/to/repo1")
            val localRepo2 = LocalRepo("/path/to/repo2")

            val rehash1 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo1)
            val rehash2 = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo2)

            assertNotEquals(rehash1, rehash2)
        }

        it("produces 64-char hex string (SHA-256)") {
            val localRepo = LocalRepo("/path/to/repo")
            localRepo.remoteOrigin = "https://github.com/user/repo.git"

            val rehash = RehashCalculatorService.calculateRepoRehash(
                "abc123", localRepo)

            assertEquals(64, rehash.length)
            assertTrue(rehash.matches(Regex("[0-9a-f]+")))
        }
    }
})

private fun assertTrue(condition: Boolean, message: String = "") {
    kotlin.test.assertTrue(condition, message)
}
