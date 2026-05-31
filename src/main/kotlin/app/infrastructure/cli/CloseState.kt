package app.infrastructure.cli

import app.infrastructure.config.Container

/**
 * On application close console UI state.
 */
class CloseState(private val container: Container) : ConsoleState {

    override fun doAction() {
        container.logger.print("You could use console commands to control repositories.",
            indentLine = true)
        container.logger.print("For more info run application with flag --help.")
        container.logger.print("Feel free to contact us on any problem by support@sourcerer.io.")
    }

    override fun next() {
        // Terminal state — no transition.
    }
}
