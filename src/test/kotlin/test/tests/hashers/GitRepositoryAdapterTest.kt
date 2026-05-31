package test.tests.hashers

import app.domain.entities.Author
import app.domain.entities.LocalRepo
import app.infrastructure.git.JGitRepositoryAdapter
import app.infrastructure.logging.SentryLoggerAdapter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import test.utils.TestRepo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for JGitRepositoryAdapter (GitRepositoryPort implementation).
 * Validates:
 * - isValidRepo detection
 * - fetchRehashesAndAuthors extraction
 * - parseGitConfig reading
 */
class GitRepositoryAdapterTest : Spek({
    val logger = SentryLoggerAdapter()
    val gitAdapter = JGitRepositoryAdapter(logger)

    fun cleanRepos() {
        Runtime.getRuntime().exec("src/test/delete_repo.sh").waitFor()
    }

    cleanRepos()

    given("isValidRepo") {
        val testRepoPath = "../testrepo-hex-valid-repo"
        val testRepo = TestRepo(testRepoPath)
        val author = Author("Test", "test@test.com")

        testRepo.createFile("file.txt", listOf("content"))
        testRepo.commit(message = "Initial commit", author = author)

        it("returns true for valid repo") {
            assertTrue(gitAdapter.isValidRepo(testRepoPath))
        }

        it("returns false for invalid path") {
            assertFalse(gitAdapter.isValidRepo("/nonexistent/path"))
        }

        it("returns false for non-git directory") {
            assertFalse(gitAdapter.isValidRepo("/tmp"))
        }

        afterGroup { testRepo.destroy() }
    }

    given("fetchRehashesAndAuthors") {
        val testRepoPath = "../testrepo-hex-fetch-authors"
        val testRepo = TestRepo(testRepoPath)
        val author1 = Author("Author1", "author1@test.com")
        val author2 = Author("Author2", "author2@test.com")

        testRepo.createFile("file1.txt", listOf("line1"))
        testRepo.commit(message = "Commit 1", author = author1)

        testRepo.createFile("file2.txt", listOf("line2"))
        testRepo.commit(message = "Commit 2", author = author2)

        testRepo.createFile("file3.txt", listOf("line3"))
        testRepo.commit(message = "Commit 3", author = author1)

        it("returns correct rehashes count") {
            val (rehashes, _, _) = gitAdapter.fetchRehashesAndAuthors(testRepoPath)
            assertEquals(3, rehashes.size)
        }

        it("returns correct authors") {
            val (_, authors, _) = gitAdapter.fetchRehashesAndAuthors(testRepoPath)
            assertEquals(2, authors.size)
            assertTrue(authors.any { it.email == "author1@test.com" })
            assertTrue(authors.any { it.email == "author2@test.com" })
        }

        it("returns correct commit counts per email") {
            val (_, _, commitsCount) = gitAdapter.fetchRehashesAndAuthors(testRepoPath)
            assertEquals(2, commitsCount["author1@test.com"])
            assertEquals(1, commitsCount["author2@test.com"])
        }

        afterGroup { testRepo.destroy() }
    }

    given("parseGitConfig") {
        val testRepoPath = "../testrepo-hex-parse-config"
        val testRepo = TestRepo(testRepoPath)
        val author = Author("ConfigUser", "config@test.com")

        testRepo.createFile("file.txt", listOf("content"))
        testRepo.commit(message = "Initial", author = author)

        it("reads user name and email from git config") {
            val localRepo = LocalRepo(testRepoPath)
            gitAdapter.parseGitConfig(testRepoPath, localRepo)

            assertEquals(testRepo.userName, localRepo.author.name)
            assertEquals(testRepo.userEmail, localRepo.author.email)
        }

        afterGroup { testRepo.destroy() }
    }

    cleanRepos()
})
