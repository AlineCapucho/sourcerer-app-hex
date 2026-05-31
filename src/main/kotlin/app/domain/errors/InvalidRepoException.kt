package app.domain.errors

/**
 * Thrown when a repository path is invalid or inaccessible.
 */
class InvalidRepoException(path: String)
    : DomainError("Invalid repository at $path")
