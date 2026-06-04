package app.domain.services

import app.domain.entities.Author
import app.domain.entities.Fact

/**
 * Domain service interface for calculating meta-level repository facts.
 *
 * Meta facts include team size (with author deduplication), commit share,
 * and average commit share per author. The implementation uses 3-gram
 * Jaccard similarity to deduplicate authors who use multiple emails.
 */
interface MetaHashingService {
    /**
     * Calculates meta facts for a repository.
     * @param repoRehash the repository rehash identifier
     * @param authors set of all authors who contributed to the repo
     * @param commitsCount map of email -> number of commits
     * @param userEmails list of current user's emails
     * @return list of computed facts (team size, commit share, avg commit share)
     */
    fun calculateMetaFacts(repoRehash: String,
                           authors: Set<Author>,
                           commitsCount: Map<String, Int>,
                           userEmails: List<String>): List<Fact>
}
