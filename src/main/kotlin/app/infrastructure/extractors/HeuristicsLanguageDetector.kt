package app.infrastructure.extractors

import app.domain.services.LanguageDetectionService
import app.domain.valueobjects.CommitStats
import app.domain.valueobjects.DiffFile

/**
 * Adapter: Implements LanguageDetectionService using heuristics and classifiers.
 *
 * This lives in infrastructure because it depends on external model files,
 * regex-based heuristics, and classifier configuration. The domain only
 * knows about the LanguageDetectionService interface.
 *
 * Delegates to the Extractor subsystem which handles:
 * - File extension and content-based language detection (Heuristics)
 * - Library usage detection via classifiers (ClassifierManager)
 * - Per-language import extraction
 */
class HeuristicsLanguageDetector : LanguageDetectionService {

    private val extractor = Extractor()

    override fun detectAndAnalyze(file: DiffFile): List<CommitStats>? {
        return extractor.extract(file)
    }
}
