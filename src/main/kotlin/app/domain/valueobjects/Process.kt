package app.domain.valueobjects

/**
 * Describes processing of multiple repos.
 */
data class Process(
    var id: Int = 0,
    var requestNumEntries: Int = 0,
    var entries: List<ProcessEntry> = mutableListOf()
)
