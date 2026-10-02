package org.companerodeescuela.api.integrations.sync

import java.time.Instant
import org.companerodeescuela.api.integrations.IntegrationProvider

/** What a single sync run touched. */
data class SyncOutcome(
    val providerId: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val itemsRead: Int,
    val succeeded: Boolean,
    val failureCategory: String? = null,
) {
    val durationMillis: Long
        get() = finishedAt.toEpochMilli() - startedAt.toEpochMilli()
}

/**
 * Adapter that pulls a bounded slice of data from an external system into the
 * platform's own store.
 *
 * Sync is pull-only and idempotent in this phase. There is no push, no
 * webhook ingestion and no bidirectional write, because the platform is not
 * yet the system of record for any institutional data.
 */
interface SyncProvider : IntegrationProvider {

    /** Short name of the dataset, for example `academic-load`. */
    val dataset: String

    /**
     * Runs one sync pass.
     *
     * @param externalId person whose data is being synchronised.
     * @param since lower bound; providers must return only newer records.
     */
    suspend fun sync(externalId: String, since: Instant?): SyncOutcome
}
