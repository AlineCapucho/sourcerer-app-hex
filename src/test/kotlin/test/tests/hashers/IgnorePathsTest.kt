package test.tests.hashers

import app.domain.entities.*
import app.infrastructure.git.JGitRepositoryAdapter
import app.infrastructure.logging.SentryLoggerAdapter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import test.utils.TestRepo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for .sourcerer-conf ignore paths functionality.
 * Validates that files listed in [ignore] section are excluded from stats.
 */
class IgnorePathsTest : Spek({
    val logger = SentryLoggerAdapter()
    val gitAdapter = JGitRepositoryAdapter(logger)

    fun cleanRepos() {
        Runtime.getRuntime().exec("src/test/delete_repo.sh").waitFor()
    }

    val userName = "Contributor"
    val userEmail = "test@domain.com"

    cleanRepos()

    given("commits with .sourcerer-conf ignore rules") {
        val lines = listOf("x = 1", "y = 2", "z = 3")
        val author = Author(userName, userEmail)
        val emails = hashSetOf(userEmail)

        val testRepoPath = "../testrepo-hex-ignore-paths"
        val testRepo = TestRepo(testRepoPath)
        val repo = Repo(rehash = "test-rehash", commits = listOf())

        it("ignores files listed in .sourcerer-conf") {
            // Commit 1: add test.py
            testRepo.createFile("test.py", lines)
            testRepo.commit(message = "commit1", author = author)

            // Commit 2: add ignore.py
            testRepo.createFile("ignore.py", lines)
            testRepo.commit(message = "commit2", author = author)

            // Commit 3: add config that ignores ignore.py
            testRepo.createFile(".sourcerer-conf",
                listOf("[ignore]", "ignore.py", "#test.py"))
            testRepo.commit(message = "commit3", author = author)

            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, emails, extractCoauthors = false)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            // The commit that added ignore.py should have its diffs filtered
            // out by the .sourcerer-conf retroactive rule.
            val allDiffPaths = commits.flatMap { it.diffs.map { d -> d.path } }

            // test.py should be present (not ignored, # is a comment)
            assertTrue(allDiffPaths.contains("test.py"),
                "test.py should not be ignored (commented in config)")

            // ignore.py should NOT be present (ignored by config)
            assertTrue(!allDiffPaths.contains("ignore.py"),
                "ignore.py should be ignored by .sourcerer-conf")
        }

        afterGroup { testRepo.destroy() }
    }

    cleanRepos()
})
