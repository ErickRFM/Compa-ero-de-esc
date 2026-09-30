package org.companerodeescuela.api.integrations.library

import java.time.LocalDate
import org.companerodeescuela.api.integrations.IntegrationProvider

/** A catalogue entry, as exposed by the library system. */
data class ExternalLibraryItem(
    val externalId: String,
    val title: String,
    val author: String? = null,
    val isbn: String? = null,
)

/** A loan held by a person. */
data class ExternalLoan(
    val itemExternalId: String,
    val title: String,
    val borrowedOn: LocalDate,
    val dueOn: LocalDate?,
    val renewalsUsed: Int = 0,
)

/**
 * Adapter for the library management system.
 *
 * Reads only in this phase. Loans and returns stay on the library's own
 * channels until the institution confirms the platform may write.
 */
interface LibraryProvider : IntegrationProvider {
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<ExternalLibraryItem>

    suspend fun listLoans(borrowerExternalId: String): List<ExternalLoan>

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}
