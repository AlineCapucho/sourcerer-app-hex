package app.application.usecases

import app.application.ports.ConfigurationPort
import app.application.ports.LoggerPort
import app.domain.entities.LocalRepo

/**
 * Use case: List all tracked repositories.
 */
class ListRepositoriesUseCase(
    private val configurator: ConfigurationPort,
    private val logger: LoggerPort
) {

    fun execute(): List<LocalRepo> {
        val repos = configurator.getLocalRepos()
        if (repos.isNotEmpty()) {
            logger.print("Tracked repositories:", indentLine = true)
            for (repo in repos) {
                logger.print(repo)
            }
        } else {
            logger.print("No tracked repositories", indentLine = true)
        }
        return repos
    }
}
