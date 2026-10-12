package org.companerodeescuela.feature.admin

import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminUserSummary

internal const val ADMIN_DIRECTORY_PAGE_SIZE = 20
internal enum class AdminDirectoryFilter { ALL, ACTIVE, PENDING, SUSPENDED, REVOKED }

internal fun filterAdminDirectoryUsers(
    users: List<AdminUserSummary>,
    query: String,
    filter: AdminDirectoryFilter,
): List<AdminUserSummary> {
    val search = query.trim()
    return users.filter { entry ->
        val user = entry.user
        val matchesText = search.isEmpty() ||
            user.displayName.contains(search, ignoreCase = true) ||
            user.email?.contains(search, ignoreCase = true) == true
        val matchesStatus = when (filter) {
            AdminDirectoryFilter.ALL -> true
            AdminDirectoryFilter.ACTIVE -> user.accountStatus == AccountStatus.ACTIVE
            AdminDirectoryFilter.PENDING -> user.accountStatus == AccountStatus.PENDING_APPROVAL ||
                user.accountStatus == AccountStatus.PENDING_VERIFICATION
            AdminDirectoryFilter.SUSPENDED -> user.accountStatus == AccountStatus.SUSPENDED
            AdminDirectoryFilter.REVOKED -> user.accountStatus == AccountStatus.REVOKED
        }
        matchesText && matchesStatus
    }
}

/** Bounded rendering prevents long user lists from overwhelming the scroll hierarchy. */
internal fun <T> adminDirectoryPage(items: List<T>, page: Int, pageSize: Int = ADMIN_DIRECTORY_PAGE_SIZE): List<T> {
    require(pageSize > 0)
    return items.drop(page.coerceAtLeast(0) * pageSize).take(pageSize)
}
