package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.GitRepositoryPort
import app.application.ports.LoggerPort
import app.domain.entities.LocalRepo

/**
 * Use case: Add a local repository to the tracking list.
 */
class AddRepositoryUseCase(
    private val gitRepository: GitRepositoryPort,
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort
) {

    fun execute(path: String, hashAllContributors: Boolean = false) {
        if (gitRepository.isValidRepo(path)) {
            val localRepo = LocalRepo(path)
            localRepo.hashAllContributors = hashAllContributors
            configurator.addLocalRepoPersistent(localRepo)
            configurator.saveToFile()
            logger.print("Added git repository at $path.")
            logger.info("config_changed") { "Config changed" }
        } else {
            logger.warn { "No valid git repository found at specified path $path" }
        }
    }
}
