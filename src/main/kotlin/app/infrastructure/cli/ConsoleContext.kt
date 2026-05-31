package app.infrastructure.cli

/**
 * State pattern context interface.
 */
interface ConsoleContext {
    fun changeState(state: ConsoleState)
}
