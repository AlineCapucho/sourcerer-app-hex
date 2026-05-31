package app.domain.valueobjects

/**
 * Meta info about a repository (hoster, name, URLs, etc.).
 */
data class RepoMeta(
    var hosterId: String = "",
    var service: String = "",
    var name: String = "",
    var ownerName: String = "",
    var description: String = "",
    var htmlUrl: String = "",
    var cloneUrl: String = ""
)
