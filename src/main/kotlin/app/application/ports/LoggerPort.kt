package app.application.ports

/**
 * Port for logging operations.
 * This is an output port — abstracts logging so the application
 * does not depend on a specific logging framework (Sentry, SLF4J, etc.).
 */
interface LoggerPort {
    fun info(message: () -> String)
    fun info(event: String, message: () -> String)
    fun debug(message: () -> String)
    fun warn(message: () -> String)
    fun error(e: Throwable, message: String = "")
    fun trace(message: () -> String)
    fun print(message: Any, indentLine: Boolean = false)
    fun printCommit(shortMessage: String, name: String, perc: Double)
    fun printCommitDetail(message: String)
}
