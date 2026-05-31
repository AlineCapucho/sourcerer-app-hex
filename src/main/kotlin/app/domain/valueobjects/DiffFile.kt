package app.domain.valueobjects

/**
 * Represents a file diff between two commits.
 * Note: changeType is a String here (ADD, MODIFY, DELETE, RENAME, COPY)
 * to avoid coupling with JGit's DiffEntry.ChangeType.
 */
class DiffFile(
    val path: String = "",
    val changeType: String = "",
    var old: DiffContent = DiffContent(),
    var new: DiffContent = DiffContent(),
    var lang: String = ""
) {
    val extension: String = getFileExtension(path)

    fun getAllAdded(): List<String> {
        return new.getAllDiffs()
    }

    fun getAllDeleted(): List<String> {
        return old.getAllDiffs()
    }

    companion object {
        private val specificExts = listOf(".min.js")

        fun getFileExtension(path: String): String {
            val fileName = path.substringAfterLast('/').toLowerCase()
            for (ext in specificExts) {
                if (fileName.endsWith(ext)) {
                    return ext
                }
            }
            return fileName.substringAfterLast(delimiter = '.',
                missingDelimiterValue = "")
        }
    }
}
