package app.domain.valueobjects

/**
 * Status of processing a single repo.
 */
data class ProcessEntry(
    var id: Int = 0,
    var status: Int = 0,
    var errorCode: Int = 0
)
