package app.application.usecases

import app.application.ports.LoggerPort
import app.application.ports.ServerApiPort

/**
 * Use case: Authenticate user with the remote server.
 */
class AuthenticateUserUseCase(
    private val serverApi: ServerApiPort,
    private val logger: LoggerPort
) {

    fun execute(): Boolean {
        val result = serverApi.authorize()
        return if (result.isSuccess()) {
            logger.info { "User authenticated successfully" }
            true
        } else {
            logger.warn { "Authentication failed" }
            false
        }
    }
}
