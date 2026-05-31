package app.domain.services

import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffFile

/**
 * Domain service for extracting technology statistics from file diffs.
 *
 * This service coordinates the extraction of language and library stats
 * from commit diffs. It delegates to language-specific extractors.
 */
object CommitStatsExtractorService {

    val RESTRICTED_EXTS = listOf(".min.js")

    const val TYPE_LANGUAGE = 1
    const val TYPE_LIBRARY = 2

    /**
     * Extracts commit stats from a list of diff files.
     * Filters out restricted extensions and delegates to language detection.
     */
    fun extract(files: List<DiffFile>,
                languageDetector: LanguageDetectionService): List<CommitStats> {
        return files
            .filter { file -> !RESTRICTED_EXTS.contains(file.extension) }
            .mapNotNull { file ->
                languageDetector.detectAndAnalyze(file)
            }
            .fold(mutableListOf()) { accStats, stats ->
                accStats.addAll(stats)
                accStats
            }
    }
}
