package test.tests.hashers

import app.domain.entities.*
import app.domain.valueobjects.CommitStats
import app.infrastructure.git.JGitRepositoryAdapter
import app.infrastructure.logging.SentryLoggerAdapter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import test.utils.MockServerApi
import test.utils.TestRepo
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for commit hashing functionality.
 * Validates the same scenarios as the original CommitHasherTest:
 * - Repo with initial commit and no history
 * - Repo with initial commit already known
 * - Happy path: added one commit
 * - Commits with language stats
 * - Commits with multiple authors (co-authors)
 */
class CommitHasherTest : Spek({
    val logger = SentryLoggerAdapter()
    val gitAdapter = JGitRepositoryAdapter(logger)

    fun cleanRepos() {
        Runtime.getRuntime().exec("src/test/delete_repo.sh").waitFor()
    }

    val userName = "First Contributor"
    val userEmail = "test@domain.com"
    val secondUserName = "Second Contributor"
    val secondUserEmail = "test2@domain.com"
    val emails = hashSetOf(userEmail, secondUserEmail)

    cleanRepos()

    given("repo with initial commit - observable produces commits") {
        val testRepoPath = "../testrepo-hex-commit-hasher-initial"
        val testRepo = TestRepo(testRepoPath)
        val author = Author(userName, userEmail)

        testRepo.createFile("test.txt", listOf("hello"))
        testRepo.commit(message = "Initial commit", author = author)

        val repo = Repo(rehash = "test-rehash", commits = listOf())
        val mockApi = MockServerApi(mockRepo = repo)

        it("produces commits from observable") {
            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, emails, extractCoauthors = false)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            assertEquals(1, commits.size)
            assertEquals(userEmail, commits[0].author.email)
        }

        afterGroup { testRepo.destroy() }
    }

    given("happy path: added one commit after initial") {
        val testRepoPath = "../testrepo-hex-commit-hasher-added"
        val testRepo = TestRepo(testRepoPath)
        val author = Author(userName, userEmail)

        testRepo.createFile("test.txt", listOf("hello"))
        testRepo.commit(message = "Initial commit", author = author)

        testRepo.createFile("test2.txt", listOf("world"))
        testRepo.commit(message = "Second commit", author = author)

        val repo = Repo(rehash = "test-rehash", commits = listOf())
        val mockApi = MockServerApi(mockRepo = repo)

        it("produces two commits") {
            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, emails, extractCoauthors = false)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            assertEquals(2, commits.size)
        }

        afterGroup { testRepo.destroy() }
    }

    given("commits with language stats (kotlin files)") {
        val testRepoPath = "../testrepo-hex-commit-hasher-kotlin"
        val testRepo = TestRepo(testRepoPath)
        val author = Author(userName, userEmail)

        val lines = listOf("fun main() {", "    println(\"Hello\")", "}")

        testRepo.createFile("Main.kt", lines)
        testRepo.commit(message = "Add kotlin file", author = author)

        val repo = Repo(rehash = "test-rehash", commits = listOf())

        it("detects kotlin language in diffs") {
            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, emails, extractCoauthors = false)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            assertEquals(1, commits.size)
            assertTrue(commits[0].diffs.isNotEmpty())
            assertTrue(commits[0].numLinesAdded > 0)
        }

        afterGroup { testRepo.destroy() }
    }

    given("commit with multiple authors (co-authors)") {
        val testRepoPath = "../testrepo-hex-commit-hasher-coauthors"
        val testRepo = TestRepo(testRepoPath)
        val author1 = Author(userName, userEmail)
        val author2 = Author(secondUserName, secondUserEmail)

        val lines = listOf("line 1", "line 2", "line 3")

        testRepo.createFile("file.txt", lines)
        val message = "Add file\n\nCo-authored-by: ${author2.name} <${author2.email}>"
        testRepo.commit(message = message, author = author1)

        val repo = Repo(rehash = "test-rehash", commits = listOf())

        it("extracts co-authors from commit message") {
            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, emails, extractCoauthors = true)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            assertEquals(1, commits.size)
            assertEquals(1, commits[0].coauthors.size)
            assertEquals(secondUserEmail, commits[0].coauthors[0].email)
        }

        afterGroup { testRepo.destroy() }
    }

    given("filtering commits by email") {
        val testRepoPath = "../testrepo-hex-commit-hasher-filter"
        val testRepo = TestRepo(testRepoPath)
        val author1 = Author(userName, userEmail)
        val author2 = Author("Other", "other@domain.com")

        testRepo.createFile("file1.txt", listOf("line1"))
        testRepo.commit(message = "Commit by author1", author = author1)

        testRepo.createFile("file2.txt", listOf("line2"))
        testRepo.commit(message = "Commit by author2", author = author2)

        testRepo.createFile("file3.txt", listOf("line3"))
        testRepo.commit(message = "Commit by author1 again", author = author1)

        val repo = Repo(rehash = "test-rehash", commits = listOf())
        val filteredEmails = hashSetOf(userEmail)

        it("only returns commits from filtered emails") {
            val observable = gitAdapter.getCommitsObservable(
                testRepoPath, repo, filteredEmails, extractCoauthors = false)

            val commits = mutableListOf<Commit>()
            observable.subscribe { commit -> commits.add(commit) }

            assertEquals(2, commits.size)
            commits.forEach { commit ->
                assertEquals(userEmail, commit.author.email)
            }
        }

        afterGroup { testRepo.destroy() }
    }

    cleanRepos()
})
