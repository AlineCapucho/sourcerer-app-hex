package test.tests.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.LoggerPort
import app.application.usecases.ListRepositoriesUseCase
import app.domain.entities.LocalRepo
import app.domain.entities.User
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ListRepositoriesUseCase.
 * Validates:
 * - Listing repos when repos exist
 * - Empty list message when no repos
 */
class ListRepositoriesUseCaseTest : Spek({

    given("repos exist") {
        val repos = listOf(LocalRepo("/path/repo1"), LocalRepo("/path/repo2"))
        val logs = mutableListOf<String>()

        val mockConfig = createMockConfig(repos)
        val mockLogger = createMockLogger(logs)
        val useCase = ListRepositoriesUseCase(mockConfig, mockLogger)

        it("returns the list of repos") {
            val result = useCase.execute()
            assertEquals(2, result.size)
            assertEquals("/path/repo1", result[0].path)
            assertEquals("/path/repo2", result[1].path)
        }

        it("prints tracked repositories header") {
            logs.clear()
            useCase.execute()
            assertTrue(logs.any { it.contains("Tracked repositories") })
        }
    }

    given("no repos") {
        val logs = mutableListOf<String>()

        val mockConfig = createMockConfig(emptyList())
        val mockLogger = createMockLogger(logs)
        val useCase = ListRepositoriesUseCase(mockConfig, mockLogger)

        it("returns empty list") {
            val result = useCase.execute()
            assertEquals(0, result.size)
        }

        it("prints no repositories message") {
            logs.clear()
            useCase.execute()
            assertTrue(logs.any { it.contains("No tracked repositories") })
        }
    }
})

private fun createMockConfig(repos: List<LocalRepo>): ConfigurationPort {
    return object : ConfigurationPort {
        override fun getUsername() = ""
        override fun getPassword() = ""
        override fun isValidCredentials() = false
        override fun getLocalRepos() = repos
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
}

private fun createMockLogger(logs: MutableList<String>): LoggerPort {
    return object : LoggerPort {
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
}
