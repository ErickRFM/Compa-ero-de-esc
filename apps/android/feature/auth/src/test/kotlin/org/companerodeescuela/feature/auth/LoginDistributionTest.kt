package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LoginDistributionTest {
    @Test
    fun `academic login contains no administrative cards`() {
        assertEquals(
            listOf(AppExperience.STUDENT, AppExperience.TEACHER, AppExperience.TUTOR),
            loginAccessesFor(AppDistribution.ACADEMIC).map { it.experience },
        )
    }

    @Test
    fun `management login contains only authorized staff experiences`() {
        assertEquals(
            listOf(AppExperience.PRINCIPAL, AppExperience.COORDINATOR, AppExperience.ADMIN, AppExperience.SUPER_ADMIN),
            loginAccessesFor(AppDistribution.MANAGEMENT).map { it.experience },
        )
    }

    @Test
    fun `management login does not expose the student registration action`() {
        assertFalse(loginAccessesFor(AppDistribution.MANAGEMENT).any { it.canRegister })
    }

    @Test
    fun `legacy audience remains backward compatible`() {
        assertEquals(loginAccesses, loginAccessesFor(AppDistribution.LEGACY))
    }
}
