package app.infrastructure.hashers

import app.domain.entities.AuthorDistance
import app.domain.entities.Repo
import app.domain.services.AuthorDistanceService
import app.domain.services.CommitPathData
import java.util.concurrent.TimeUnit

/**
 * Adapter: Implements AuthorDistanceService.
 *
 * Uses a time-based algorithm to calculate collaboration distance:
 * - For user commits: stores path -> timestamp mapping
 * - For other authors: counts paths overlapping with user within 365 days
 * - Score is the absolute count of overlapping paths
 */
class AuthorDistanceAdapter : AuthorDistanceService {

    override fun calculateDistances(commitData: List<CommitPathData>,
                                    repoRehash: String,
                                    emails: HashSet<String>,
                                    userEmails: HashSet<String>): List<AuthorDistance> {
        val serverRepo = Repo(rehash = repoRehash)
        val authorScores = hashMapOf<String, Double>()
        emails.forEach { authorScores[it] = 0.0 }

        // Store the time of the earliest commit for a path by user.
        val authorPathLastContribution = hashMapOf<String, Long>()

        for (data in commitData) {
            val email = data.email
            val paths = data.paths
            val time = data.timestamp

            if (email in userEmails) {
                paths.forEach { path ->
                    authorPathLastContribution[path] = time
                }
            } else {
                val score = paths
                    .filter { path -> path in authorPathLastContribution }
                    .filter { path ->
                        val authorTime = authorPathLastContribution[path]!!
                        val timeDelta = TimeUnit.DAYS.convert(
                            authorTime - time, TimeUnit.SECONDS)
                        timeDelta < 365
                    }.size
                authorScores[email] = (authorScores[email] ?: 0.0) + score
            }
        }

        val stats = mutableListOf<AuthorDistance>()
        authorScores.forEach { (email, value) ->
            if (email !in userEmails) {
                stats.add(AuthorDistance(serverRepo, email, value))
            }
        }
        return stats
    }
}
