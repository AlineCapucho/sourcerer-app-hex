package app.infrastructure.cli

import app.infrastructure.config.Container

/**
 * On application open console UI state.
 */
class OpenState(
    private val context: ConsoleContext,
    private val container: Container
) : ConsoleState {

    override fun doAction() {
        if (!container.configurator.isValidCredentials()) {
            container.logger.print("Sourcerer hashes your git repositories into " +
                "intelligent engineering profiles.")
            container.logger.print("If you don't have an account, please, sign up at " +
                "https://sourcerer.io/join")
        } else {
            container.logger.print("Sourcerer. Use flag --help to list available commands.")
        }
    }

    override fun next() {
        context.changeState(AuthState(context, container))
    }
}
