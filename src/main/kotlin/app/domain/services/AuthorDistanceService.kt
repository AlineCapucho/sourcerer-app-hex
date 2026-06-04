package app.domain.services

import app.domain.entities.AuthorDistance

/**
 * Domain service interface for computing collaboration distance between
 * authors based on shared file paths within a time window.
 *
 * The algorithm uses a time-based approach:
 * - For user commits: stores path -> timestamp mapping
 * - For other authors: counts paths that overlap with user within 365 days
 * - Score is the absolute count of overlapping paths
 */
interface AuthorDistanceService {
    /**
     * Processes commit path data to compute author distance scores.
     * @param commitData list of commit path data (email, paths, timestamp)
     * @param repoRehash the repository rehash identifier
     * @param emails all emails contributing to the repo
     * @param userEmails the current user's emails
     * @return list of author distances with scores
     */
    fun calculateDistances(commitData: List<CommitPathData>,
                           repoRehash: String,
                           emails: HashSet<String>,
                           userEmails: HashSet<String>): List<AuthorDistance>
}

/**
 * Data class representing commit path information needed for distance
 * calculation.
 */
data class CommitPathData(
    val email: String,
    val paths: List<String>,
    val timestamp: Long
)
