package app.domain.valueobjects

/**
 * User email with status information.
 */
class UserEmail(
    var email: String = "",
    var primary: Boolean = false,
    var verified: Boolean = false
) {
    override fun toString(): String {
        val primaryStr = if (this.primary) " (Primary)" else ""
        val verifiedStr = if (this.verified) "Confirmed" else "Not confirmed"
        return "${this.email}$primaryStr — $verifiedStr"
    }

    override fun equals(other: Any?): Boolean {
        if (other is UserEmail) {
            return email == other.email
        }
        return false
    }

    override fun hashCode(): Int {
        return email.hashCode()
    }
}
