package app.domain.errors

/**
 * Thrown when a repository has no commits or no valid branch head.
 */
class EmptyRepoException(message: String = "Repository is empty")
    : DomainError(message)
