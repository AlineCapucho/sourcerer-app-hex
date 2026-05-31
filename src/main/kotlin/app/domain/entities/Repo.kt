package app.domain.entities

import app.domain.valueobjects.RepoMeta

/**
 * Repository identity and server state.
 */
data class Repo(
    var rehash: String = "",
    var initialCommitRehash: String = "",
    var emails: List<String> = listOf(),
    var commits: List<Commit> = listOf(),
    var meta: RepoMeta = RepoMeta(),
    var processEntryId: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        return rehash == (other as Repo).rehash
    }

    override fun hashCode(): Int {
        return rehash.hashCode()
    }
}
