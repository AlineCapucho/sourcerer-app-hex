package app.domain.entities

import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffFile

/**
 * Commit with associated statistics.
 */
data class Commit(
    var rehash: String = "",
    var repo: Repo = Repo(),
    var treeRehash: String = "",
    var author: Author = Author(),
    var coauthors: List<Author> = mutableListOf(),
    var dateTimestamp: Long = 0,
    var dateTimeZoneOffset: Int = 0,
    var isQommit: Boolean = false,
    var numLinesAdded: Int = 0,
    var numLinesDeleted: Int = 0,
    var stats: List<CommitStats> = mutableListOf()
) {
    var diffs: List<DiffFile> = listOf()

    fun getAllAdded(): List<String> {
        return diffs.map { it.getAllAdded() }.flatten()
    }

    fun getAllDeleted(): List<String> {
        return diffs.map { it.getAllDeleted() }.flatten()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        return rehash == (other as Commit).rehash
    }

    override fun hashCode(): Int {
        return rehash.hashCode()
    }
}
