package app.domain.services

import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffFile

/**
 * Domain service interface for language detection and analysis.
 *
 * This defines the contract for detecting programming languages from file
 * diffs and extracting technology statistics. The actual implementation
 * (heuristics, classifiers) lives in the infrastructure layer since it
 * depends on external model files and configuration.
 */
interface LanguageDetectionService {

    /**
     * Detects the language of a diff file and returns the associated
     * commit stats (language lines, library usage, etc.).
     * Returns null if the file type is not recognized.
     */
    fun detectAndAnalyze(file: DiffFile): List<CommitStats>?
}
