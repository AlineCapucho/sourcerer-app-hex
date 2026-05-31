package test.tests.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.LoggerPort
import app.application.usecases.RemoveRepositoryUseCase
import app.domain.entities.LocalRepo
import app.domain.entities.User
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for RemoveRepositoryUseCase.
 * Validates:
 * - Removing an existing repository
 * - Handling null path gracefully
 */
class RemoveRepositoryUseCaseTest : Spek({

    given("removing a repository") {
        val removedRepos = mutableListOf<LocalRepo>()
        val logs = mutableListOf<String>()
        var saved = false

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
            override fun removeLocalRepoPersistent(localRepo: LocalRepo) {
                removedRepos.add(localRepo)
            }
            override fun setUser(user: User) {}
            override fun isFirstLaunch() = true
            override fun loadFromFile() {}
            override fun saveToFile() { saved = true }
            override fun resetAndSave() {}
        }

        val mockLogger = object : LoggerPort {
            override fun info(message: () -> String) {}
            override fun info(event: String, message: () -> String) { logs.add(message()) }
            override fun debug(message: () -> String) {}
            override fun warn(message: () -> String) {}
            override fun error(e: Throwable, message: String) {}
            override fun trace(message: () -> String) {}
            override fun print(message: Any, indentLine: Boolean) { logs.add(message.toString()) }
            override fun printCommit(shortMessage: String, name: String, perc: Double) {}
            override fun printCommitDetail(message: String) {}
        }

        val useCase = RemoveRepositoryUseCase(mockConfig, mockLogger)

        it("removes repo and saves config") {
            useCase.execute("/path/to/repo")
            assertEquals(1, removedRepos.size)
            assertEquals("/path/to/repo", removedRepos[0].path)
            assertTrue(saved)
        }

        it("prints confirmation message") {
            logs.clear()
            useCase.execute("/path/to/repo")
            assertTrue(logs.any { it.contains("removed") })
        }
    }

    given("null path") {
        val logs = mutableListOf<String>()

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
            override fun warn(message: () -> String) {}
            override fun error(e: Throwable, message: String) {}
            override fun trace(message: () -> String) {}
            override fun print(message: Any, indentLine: Boolean) { logs.add(message.toString()) }
            override fun printCommit(shortMessage: String, name: String, perc: Double) {}
            override fun printCommitDetail(message: String) {}
        }

        val useCase = RemoveRepositoryUseCase(mockConfig, mockLogger)

        it("prints not found message") {
            logs.clear()
            useCase.execute(null)
            assertTrue(logs.any { it.contains("not found") })
        }
    }
})
