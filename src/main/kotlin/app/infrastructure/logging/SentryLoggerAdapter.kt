package app.infrastructure.logging

import app.BuildConfig
import app.application.ports.LoggerPort
import io.sentry.Sentry
import io.sentry.event.Breadcrumb
import io.sentry.event.BreadcrumbBuilder
import io.sentry.event.UserBuilder
import java.util.*

/**
 * Adapter: Implements LoggerPort using Sentry and console output.
 * This is an output adapter — it lives in the infrastructure layer
 * and implements the port defined in the application layer.
 */
class SentryLoggerAdapter : LoggerPort {

    private val LEVEL: Int
    private val SILENT = BuildConfig.SILENT_USER_OUTPUT
    private val SENTRY_ENABLED = BuildConfig.SENTRY_ENABLED
    private val PRINT_STACK_TRACE = BuildConfig.PRINT_STACK_TRACE

    companion object {
        private const val ERROR = 0
        private const val WARN = 1
        private const val INFO = 2
        private const val DEBUG = 3
        private const val TRACE = 4
    }

    var username: String? = null
        set(value) {
            field = value
            if (SENTRY_ENABLED) {
                Sentry.getContext()?.user =
                    UserBuilder().setUsername(value).build()
            }
        }

    init {
        if (SENTRY_ENABLED) {
            Sentry.init(BuildConfig.SENTRY_DSN)
            addTags()
        }
        LEVEL = configLevelValue()
    }

    private fun configLevelValue(): Int {
        val levels = mapOf("trace" to TRACE, "debug" to DEBUG, "info" to INFO,
            "warn" to WARN, "error" to ERROR)
        return levels.getValue(BuildConfig.LOG_LEVEL)
    }

    override fun info(message: () -> String) {
        val msg = message()
        if (LEVEL >= INFO) {
            println("[i] $msg.")
        }
        addBreadcrumb(msg, Breadcrumb.Level.INFO)
    }

    override fun info(event: String, message: () -> String) {
        val msg = message()
        if (LEVEL >= INFO) {
            println("[i] $msg.")
        }
        addBreadcrumb(msg, Breadcrumb.Level.INFO)
    }

    override fun debug(message: () -> String) {
        if (LEVEL >= DEBUG) {
            println("[d] ${message()}.")
        }
    }

    override fun warn(message: () -> String) {
        val msg = message()
        if (LEVEL >= WARN) {
            println("[w] $msg.")
        }
        addBreadcrumb(msg, Breadcrumb.Level.WARNING)
    }

    override fun error(e: Throwable, message: String) {
        val finalMessage = if (message.isNotBlank()) { "$message: " }
        else { "" } + e.message
        if (LEVEL >= ERROR) {
            println("[e] $finalMessage")
            if (PRINT_STACK_TRACE) {
                e.printStackTrace()
            }
        }
        if (SENTRY_ENABLED) {
            Sentry.capture(e)
        }
        addBreadcrumb(finalMessage, Breadcrumb.Level.ERROR)
    }

    override fun trace(message: () -> String) {
        if (LEVEL >= TRACE) {
            println("[t] ${message()}.")
        }
    }

    override fun print(message: Any, indentLine: Boolean) {
        if (!SILENT) {
            if (indentLine) {
                println()
            }
            println(message.toString())
        }
    }

    override fun printCommit(shortMessage: String, name: String, perc: Double) {
        if (!SILENT) {
            val percentsStr = java.lang.String.format("%6.2f", perc)
            val hash = name.substring(0, minOf(7, name.length))
            val messageTrim = if (shortMessage.length > 59) {
                shortMessage.substring(0, 56).plus("...")
            } else shortMessage
            println(" [$percentsStr%] * $hash $messageTrim")
        }
    }

    override fun printCommitDetail(message: String) {
        if (!SILENT) {
            val indent = "           |        "
            val messageTrim = if (message.length > 59) {
                message.substring(0, 56).plus("...")
            } else message
            println(indent + messageTrim)
        }
    }

    private fun addBreadcrumb(message: String, level: Breadcrumb.Level) {
        if (SENTRY_ENABLED) {
            Sentry.getContext()?.recordBreadcrumb(BreadcrumbBuilder()
                .setMessage(message)
                .setLevel(level)
                .setTimestamp(Date())
                .build())
        }
    }

    private fun addTags() {
        if (SENTRY_ENABLED) {
            val ctx = Sentry.getContext()
            val default = "unavailable"
            ctx?.addTag("environment", BuildConfig.ENV)
            ctx?.addTag("log-level", BuildConfig.LOG_LEVEL)
            ctx?.addTag("version", BuildConfig.VERSION)
            ctx?.addTag("version-code", BuildConfig.VERSION_CODE.toString())
            ctx?.addTag("os-name", System.getProperty("os.name", default))
            ctx?.addTag("os-version", System.getProperty("os.version", default))
            ctx?.addTag("java-vendor", System.getProperty("java.vendor", default))
            ctx?.addTag("java-version", System.getProperty("java.version", default))
        }
    }
}
