package app.infrastructure.cli

import app.infrastructure.config.Container

/**
 * Interactive console user interface using state pattern.
 * This is an input adapter — drives the application through
 * an interactive terminal session.
 */
class ConsoleUi(private val container: Container) : ConsoleContext {
    var state: ConsoleState = OpenState(this, container)

    init {
        changeState(state)
    }

    override fun changeState(state: ConsoleState) {
        this.state = state
        state.doAction()
        state.next()
    }
}
