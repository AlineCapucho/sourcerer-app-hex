package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.LoggerPort
import app.domain.entities.LocalRepo

/**
 * Use case: Remove a repository from the tracking list.
 */
class RemoveRepositoryUseCase(
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort
) {

    fun execute(path: String?) {
        if (path != null) {
            configurator.removeLocalRepoPersistent(LocalRepo(path))
            configurator.saveToFile()
            logger.print("Repository removed from tracking list.")
            logger.info("config_changed") { "Config changed" }
        } else {
            logger.print("Repository not found in tracking list.")
        }
    }
}
