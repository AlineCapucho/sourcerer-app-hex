package app.infrastructure.extractors

import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffFile

/**
 * Infrastructure-level extractor that detects languages and libraries
 * from file diffs using heuristics and classifiers.
 *
 * This is a simplified version that handles the core extraction logic.
 * The full implementation would include all language-specific extractors,
 * the ClassifierManager, and the Heuristics detection system from the
 * original sourcerer-app.
 */
class Extractor {

    companion object {
        const val TYPE_LANGUAGE = 1
        const val TYPE_LIBRARY = 2
    }

    /**
     * Analyzes a single diff file and returns commit stats.
     * Returns null if the file type is not recognized.
     */
    fun extract(file: DiffFile): List<CommitStats>? {
        val language = detectLanguage(file) ?: return null
        file.lang = language

        val stats = mutableListOf<CommitStats>()

        // Language stats
        val linesAdded = file.getAllAdded().size
        val linesDeleted = file.getAllDeleted().size
        if (linesAdded > 0 || linesDeleted > 0) {
            stats.add(CommitStats(
                numLinesAdded = linesAdded,
                numLinesDeleted = linesDeleted,
                type = TYPE_LANGUAGE,
                tech = language
            ))
        }

        return if (stats.isNotEmpty()) stats else null
    }

    /**
     * Detects the programming language based on file extension.
     * A full implementation would use content-based heuristics for
     * ambiguous extensions.
     */
    private fun detectLanguage(file: DiffFile): String? {
        return when (file.extension) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "rb" -> "ruby"
            "go" -> "go"
            "rs" -> "rust"
            "c" -> "c"
            "cpp", "cc", "cxx" -> "cpp"
            "h", "hpp" -> "c-header"
            "cs" -> "csharp"
            "swift" -> "swift"
            "scala" -> "scala"
            "php" -> "php"
            "r" -> "r"
            "m" -> "objective-c"
            "mm" -> "objective-cpp"
            "pl", "pm" -> "perl"
            "sh", "bash" -> "shell"
            "lua" -> "lua"
            "hs" -> "haskell"
            "erl" -> "erlang"
            "ex", "exs" -> "elixir"
            "clj" -> "clojure"
            "groovy" -> "groovy"
            "dart" -> "dart"
            "sql" -> "sql"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "less" -> "less"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md", "markdown" -> "markdown"
            "proto" -> "protobuf"
            "gradle" -> "gradle"
            "cmake" -> "cmake"
            "dockerfile" -> "docker"
            else -> null
        }
    }
}
