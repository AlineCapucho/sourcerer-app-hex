package app.domain.entities

import app.domain.valueobjects.UserEmail

/**
 * User information with repos and emails.
 */
data class User(
    var repos: MutableList<Repo> = mutableListOf(),
    var emails: HashSet<UserEmail> = hashSetOf()
)
