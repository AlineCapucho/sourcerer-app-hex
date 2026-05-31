package app.domain.entities

/**
 * Commit author identified by email.
 */
data class Author(
    var name: String = "",
    var email: String = "",
    var repo: Repo = Repo()
) {
    // Email defines user identity.
    override fun equals(other: Any?): Boolean {
        if (other is Author) {
            return email == other.email
        }
        return false
    }

    override fun hashCode(): Int {
        return email.hashCode()
    }
}
