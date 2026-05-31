package app.domain.entities

import app.domain.valueobjects.RepoMeta

/**
 * Local filesystem repository reference.
 */
data class LocalRepo(var path: String = "") {
    var hashAllContributors: Boolean = false
    var author: Author = Author()
    var remoteOrigin: String = ""
    var meta: RepoMeta = RepoMeta()
    var processEntryId: Int? = 0

    override fun toString(): String {
        return path
    }
}
