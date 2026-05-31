package app.domain.entities

/**
 * Collaboration distance score between authors.
 */
data class AuthorDistance(
    var repo: Repo = Repo(),
    var email: String = "",
    var score: Double = 0.0
)
