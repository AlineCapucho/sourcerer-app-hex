package app.domain.services

import app.domain.entities.Fact

/**
 * Domain service interface for computing code line longevity and colleague facts.
 * Tracks how long code lines survive in the repository and detects collaboration patterns.
 */
interface CodeLongevityService {
    /**
     * Calculates longevity facts for the repository.
     * @param repoPath path to the git repository
     * @param repoRehash repository rehash identifier
     * @param emails set of user emails to track
     * @return list of LINE_LONGEVITY, LINE_LONGEVITY_REPO, and COLLEAGUES facts
     */
    fun calculateLongevityFacts(repoPath: String, repoRehash: String,
                                emails: HashSet<String>): List<Fact>
}
