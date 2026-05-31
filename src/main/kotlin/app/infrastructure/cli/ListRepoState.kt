package app.infrastructure.cli

import app.infrastructure.config.Container

/**
 * List tracked repositories console UI state.
 */
class ListRepoState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    override fun doAction() {
        val repos = container.configurator.getLocalRepos()
        if (repos.isNotEmpty()) {
            container.logger.print("Tracked repositories:", indentLine = true)
            repos.forEach { repo -> container.logger.print(repo) }
        }
    }

    override fun next() {
        context.changeState(AddRepoState(context, container))
    }
}
