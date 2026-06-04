package app.infrastructure.cli

import app.BuildConfig
import app.application.ports.ServerApiPort
import app.infrastructure.config.Container
import java.io.IOError

/**
 * Authorization console UI state.
 * Handles interactive login flow.
 */
class AuthState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    private var username = ""
    private var password = ""
    private var retry = true
    private var authorized = false

    override fun doAction() {
        if (!container.configurator.isValidCredentials()) {
            getUsername()
            getPassword()
        }

        authorized = tryAuth()
        while (!authorized && retry) {
            getPassword()
            authorized = tryAuth()
        }
    }

    override fun next() {
        if (authorized) {
            context.changeState(ListRepoState(context, container))
        } else {
            context.changeState(CloseState(container))
        }
    }

    private fun getUsername() {
        container.logger.print("Enter username:")
        username = readLine() ?: ""
        container.configurator.setUsernameCurrent(username)
    }

    private fun getPassword() {
        container.logger.print("Enter password:")
        password = readPassword()
        container.configurator.setPasswordCurrent(password)
    }

    private fun readPassword(): String {
        val console = System.console()
        return if (console != null) {
            try {
                console.readPassword().joinToString("")
            } catch (e: IOError) { "" }
        } else {
            readLine() ?: ""
        }
    }

    private fun saveCredentialsIfChanged() {
        if (username.isNotEmpty()) {
            container.configurator.setUsernamePersistent(username)
        }
        if (password.isNotEmpty()) {
            container.configurator.setPasswordPersistent(password)
        }
        if (username.isNotEmpty() || password.isNotEmpty()) {
            container.configurator.saveToFile()
        }
    }

    private fun tryAuth(): Boolean {
        try {
            container.logger.print("Signing in...")

            // Check for out-of-date app version.
            val result = container.serverApi.authorize()
            if (result.isOutOfDate()) {
                container.logger.print("App is out of date. Please get new version at " +
                    "https://sourcerer.io")
                retry = false
                return false
            }
            if (!result.isSuccess()) {
                result.onErrorThrow()
            }

            // Fetch user data from server.
            val user = container.serverApi.getUser().getOrThrow()
            container.configurator.setUser(user)

            container.logger.print("Signed in successfully. Your profile page is " +
                BuildConfig.PROFILE_URL + container.configurator.getUsername())

            saveCredentialsIfChanged()
            container.logger.info("auth") { "Auth success" }

            return true
        } catch (e: Throwable) {
            if (e is app.application.ports.ApiError || (e.message?.contains("401") == true) ||
                (e.message?.contains("403") == true)) {
                container.logger.print("Authentication error. Try again.")
            } else {
                container.logger.print("Connection problems. Try again later.")
                container.logger.error(e, "Auth error")
                retry = false
            }
        }

        return false
    }
}
