package app.infrastructure.cli

/**
 * State pattern interface for console user interface.
 */
interface ConsoleState {
    fun doAction()
    fun next()
}
