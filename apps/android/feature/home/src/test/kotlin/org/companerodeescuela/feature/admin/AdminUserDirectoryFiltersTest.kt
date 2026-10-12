package org.companerodeescuela.feature.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.UserSummary

class AdminUserDirectoryFiltersTest {
    private val users = listOf(
        entry("A", "Ana López", "ana@school.test", AccountStatus.ACTIVE),
        entry("B", "Elena Ríos", "elena@school.test", AccountStatus.PENDING_APPROVAL),
        entry("C", "María Prado", "maria@school.test", AccountStatus.PENDING_VERIFICATION),
        entry("D", "Luis Torres", null, AccountStatus.SUSPENDED),
        entry("E", "Sara Vega", null, AccountStatus.REVOKED),
    )

    private fun entry(id: String, name: String, email: String?, status: AccountStatus) =
        AdminUserSummary(UserSummary(id = id, displayName = name, email = email, accountStatus = status), emptyList())

    @Test fun `search ignores case and supports email`() {
        assertEquals(listOf("A"), filterAdminDirectoryUsers(users, "ANA", AdminDirectoryFilter.ALL).map { it.user.id })
        assertEquals(listOf("B"), filterAdminDirectoryUsers(users, "ELENA@school", AdminDirectoryFilter.ALL).map { it.user.id })
    }

    @Test fun `pending covers both registration and approval`() {
        assertEquals(listOf("B", "C"), filterAdminDirectoryUsers(users, "", AdminDirectoryFilter.PENDING).map { it.user.id })
    }

    @Test fun `statuses remain isolated and do not invent users`() {
        assertEquals(listOf("D"), filterAdminDirectoryUsers(users, "", AdminDirectoryFilter.SUSPENDED).map { it.user.id })
        assertEquals(listOf("E"), filterAdminDirectoryUsers(users, "", AdminDirectoryFilter.REVOKED).map { it.user.id })
    }

    @Test fun `pagination is stable and bounded`() {
        val values = (1..53).toList()
        assertEquals((1..20).toList(), adminDirectoryPage(values, 0))
        assertEquals((41..53).toList(), adminDirectoryPage(values, 2))
        assertEquals(emptyList(), adminDirectoryPage(values, 3))
    }
}
