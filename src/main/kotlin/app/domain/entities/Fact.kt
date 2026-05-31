package app.domain.entities

/**
 * Key-value statistics for fun facts.
 */
data class Fact(
    var repo: Repo = Repo(),
    var code: Int = 0,
    var key: Int = 0,
    var value: String = "",
    var author: Author = Author(),
    var value2: String = "",
    var value3: String = ""
)
