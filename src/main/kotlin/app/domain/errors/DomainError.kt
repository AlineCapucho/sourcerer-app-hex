package app.domain.errors

/**
 * Base class for domain-level errors.
 */
open class DomainError(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)
