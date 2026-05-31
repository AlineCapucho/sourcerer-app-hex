package app.domain.errors

/**
 * Thrown when one or more errors occur during the hashing process.
 */
class HashingException(val errors: List<Throwable>)
    : DomainError("Hashing failed with ${errors.size} error(s)")
