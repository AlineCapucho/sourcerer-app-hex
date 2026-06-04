package app.domain.services

import app.domain.entities.Commit
import app.domain.entities.Fact
import io.reactivex.Observable

/**
 * Domain service interface for calculating per-author statistical facts
 * from commit history.
 *
 * Facts include day-of-week/day-time distributions, date ranges, commit
 * counts, line averages, lines-per-commit histograms, variable naming
 * conventions, and indentation style.
 */
interface FactHashingService {
    /**
     * Processes an observable stream of commits to accumulate facts.
     * @param observable stream of commits from git history
     * @param repoRehash the repository rehash identifier
     * @param rehashes list of all commit rehashes (used for array sizing)
     * @param emails set of emails to track facts for
     * @param onError callback for error handling
     */
    fun updateFromObservable(observable: Observable<Commit>,
                             repoRehash: String,
                             rehashes: List<String>,
                             emails: HashSet<String>,
                             onError: (Throwable) -> Unit)

    /**
     * Returns the accumulated facts after observable completes.
     */
    fun getComputedFacts(): List<Fact>
}
