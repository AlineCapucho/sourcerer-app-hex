package app.domain.services

import app.domain.entities.LocalRepo
import org.apache.commons.codec.digest.DigestUtils

/**
 * Domain service for calculating repository rehashes.
 *
 * Repos may have forks. Such repos should be tracked independently.
 * Therefore, rehash of repo is calculated by values of:
 * - Rehash of initial commit;
 * - Hash of remote origin;
 * - If remote origin not presented: repo local path and username.
 */
object RehashCalculatorService {

    /**
     * Calculates the unique rehash for a repository based on its initial
     * commit and remote origin (or local path + username as fallback).
     */
    fun calculateRepoRehash(initialCommitRehash: String,
                            localRepo: LocalRepo): String {
        val username = try { System.getProperty("user.name") }
                       catch (e: Exception) { "" }

        var repoRehash = initialCommitRehash
        if (localRepo.remoteOrigin.isNotBlank()) {
            repoRehash += localRepo.remoteOrigin
        } else {
            repoRehash += localRepo.path + username
        }

        return DigestUtils.sha256Hex(repoRehash)
    }
}
