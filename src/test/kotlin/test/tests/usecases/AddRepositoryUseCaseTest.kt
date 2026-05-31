package test.tests.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.GitRepositoryPort
import app.application.ports.LoggerPort
import app.application.usecases.AddRepositoryUseCase
import app.domain.entities.Author
import app.domain.entities.Commit
import app.domain.entities.LocalRepo
import app.domain.entities.Repo
import app.domain.entities.User
import io.reactivex.Observable
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AddRepositoryUseCase.
 * Validates:
 * - Adding a valid repository
 * - Rejecting an invalid repository
 * - hashAllContributors flag propagation
 */
class AddRepositoryUseCaseTest : Spek({

    given("adding a valid repository") {
        val addedRepos = mutableListOf<LocalRepo>()
        val logs = mutableListOf<String>()

        val mockGit = object : GitRepositoryPort {
            override fun isValidRepo(path: String) = path == "/valid/repo"
            override fun fetchRehashesAndAuthors(repoPath: String) =
                Triple(listOf<String>(), hashSetOf<Author>(), hashMapOf<String, Int>())
            override fun getCommitsObservable(repoPath: String, repo: Repo,
                filteredEmails: HashSet<String>?, extractCoauthors: Boolean) =
                Observable.empty<Commit>()
            override fun parseGitConfig(repoPath: String, localRepo: LocalRepo) {}
        }

        val mockConfig = object : ConfigurationPort {
            override fun getUsername() = ""
            override fun getPassword() = ""
            override fun isValidCredentials() = false
            override fun getLocalRepos() = addedRepos.toList()
            override fun getUser() = User()
            override fun setUsernameCurrent(username: String) {}
            override fun setPasswordCurrent(password: String) {}
            override fun getUuidPersistent() = ""
            override fun setUsernamePersistent(username: String) {}
            override fun setPasswordPersistent(password: String) {}
            override fun addLocalRepoPersistent(localRepo: LocalRepo) {
                addedRepos.add(localRepo)
            }
            override fun removeLocalRepoPersistent(localRepo: LocalRepo) {}
            override fun setUser(user: User) {}
            override fun isFirstLaunch() = true
            override fun loadFromFile() {}
            override fun saveToFile() {}
            override fun resetAndSave() {}
        }

        val mockLogger = object : LoggerPort {
            override fun info(message: () -> String) { logs.add(message()) }
            override fun info(event: String, message: () -> String) { logs.add(message()) }
            override fun debug(message: () -> String) {}
            override fun warn(message: () -> String) { logs.add("WARN: ${message()}") }
            override fun error(e: Throwable, message: String) {}
            override fun trace(message: () -> String) {}
            override fun print(message: Any, indentLine: Boolean) { logs.add(message.toString()) }
            override fun printCommit(shortMessage: String, name: String, perc: Double) {}
            override fun printCommitDetail(message: String) {}
        }

        val useCase = AddRepositoryUseCase(mockGit, mockConfig, mockLogger)

        it("adds valid repo to config") {
            useCase.execute("/valid/repo", hashAllContributors = false)
            assertEquals(1, addedRepos.size)
            assertEquals("/valid/repo", addedRepos[0].path)
        }

        it("sets hashAllContributors flag") {
            addedRepos.clear()
            useCase.execute("/valid/repo", hashAllContributors = true)
            assertTrue(addedRepos[0].hashAllContributors)
        }
    }

    given("adding an invalid repository") {
        val logs = mutableListOf<String>()

        val mockGit = object : GitRepositoryPort {
            override fun isValidRepo(path: String) = false
            override fun fetchRehashesAndAuthors(repoPath: String) =
                Triple(listOf<String>(), hashSetOf<Author>(), hashMapOf<String, Int>())
            override fun getCommitsObservable(repoPath: String, repo: Repo,
                filteredEmails: HashSet<String>?, extractCoauthors: Boolean) =
                Observable.empty<Commit>()
            override fun parseGitConfig(repoPath: String, localRepo: LocalRepo) {}
        }

        val mockConfig = object : ConfigurationPort {
            override fun getUsername() = ""
            override fun getPassword() = ""
            override fun isValidCredentials() = false
            override fun getLocalRepos() = listOf<LocalRepo>()
            override fun getUser() = User()
            override fun setUsernameCurrent(username: String) {}
            override fun setPasswordCurrent(password: String) {}
            override fun getUuidPersistent() = ""
            override fun setUsernamePersistent(username: String) {}
            override fun setPasswordPersistent(password: String) {}
            override fun addLocalRepoPersistent(localRepo: LocalRepo) {}
            override fun removeLocalRepoPersistent(localRepo: LocalRepo) {}
            override fun setUser(user: User) {}
            override fun isFirstLaunch() = true
            override fun loadFromFile() {}
            override fun saveToFile() {}
            override fun resetAndSave() {}
        }

        val mockLogger = object : LoggerPort {
            override fun info(message: () -> String) {}
            override fun info(event: String, message: () -> String) {}
            override fun debug(message: () -> String) {}
            override fun warn(message: () -> String) { logs.add("WARN: ${message()}") }
            override fun error(e: Throwable, message: String) {}
            override fun trace(message: () -> String) {}
            override fun print(message: Any, indentLine: Boolean) {}
            override fun printCommit(shortMessage: String, name: String, perc: Double) {}
            override fun printCommitDetail(message: String) {}
        }

        val useCase = AddRepositoryUseCase(mockGit, mockConfig, mockLogger)

        it("logs warning for invalid repo") {
            logs.clear()
            useCase.execute("/invalid/path")
            assertTrue(logs.any { it.contains("No valid git repository") })
        }
    }
})
