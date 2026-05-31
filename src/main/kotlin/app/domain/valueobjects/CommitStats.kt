package app.domain.valueobjects

/**
 * Tech stats on a commit: lines added/deleted per technology.
 */
data class CommitStats(
    var numLinesAdded: Int = 0,
    var numLinesDeleted: Int = 0,
    var type: Int = 0,
    var tech: String = ""
)
