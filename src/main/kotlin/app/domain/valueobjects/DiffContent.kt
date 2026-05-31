package app.domain.valueobjects

/**
 * Content of one side (old or new) of a diff, with ranges marking changed lines.
 */
class DiffContent(
    val content: List<String> = listOf(),
    val ranges: List<DiffRange> = listOf(),
    var imports: List<String> = listOf()
) {
    fun getAllDiffs(): List<String> {
        return ranges.fold(mutableListOf()) { total, range ->
            total.addAll(content.subList(range.start, range.end))
            total
        }
    }
}
