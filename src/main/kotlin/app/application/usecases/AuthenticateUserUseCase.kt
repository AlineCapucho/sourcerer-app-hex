package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.LoggerPort
import app.application.ports.ServerApiPort

/**
 * Use case: Authenticate user with the remote server.
 * Performs authorization, fetches user data, and saves it locally.
 */
class AuthenticateUserUseCase(
    private val serverApi: ServerApiPort,
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort
) {

    fun execute(): Boolean {
        val result = serverApi.authorize()
        if (!result.isSuccess()) {
            return false
        }

        // Fetch user data from server and save locally.
        val userResult = serverApi.getUser()
        if (userResult.isSuccess()) {
            val user = userResult.getOrThrow()
            configurator.setUser(user)
            logger.info { "User authenticated successfully" }
        }

        return true
    }
}
