package org.companerodeescuela

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual installed APK identity, not only a pure unit-test enum.
 * The backend still authorizes every resource independently.
 */
@RunWith(AndroidJUnit4::class)
class AppDistributionSmokeTest {
    @Test
    fun installedPackageMatchesCompiledAudience() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val distribution = AppDistribution.valueOf(BuildConfig.APP_DISTRIBUTION)
        when (distribution) {
            AppDistribution.ACADEMIC -> {
                assertEquals("org.companerodeescuela", context.packageName)
                assertTrue(RoleExperienceResolver.available(setOf(UserRole.STUDENT), distribution).isNotEmpty())
                assertFalse(distribution.allows(AppExperience.ADMIN))
            }
            AppDistribution.MANAGEMENT -> {
                assertEquals("org.companerodeescuela.gestion", context.packageName)
                assertFalse(BuildConfig.FIREBASE_CONFIGURED)
                assertTrue(RoleExperienceResolver.available(setOf(UserRole.ADMIN), distribution).isNotEmpty())
                assertFalse(distribution.allows(AppExperience.STUDENT))
            }
            AppDistribution.LEGACY -> error("Distribution releases must not use the legacy audience")
        }
    }

    @Test
    fun crossProductProfileIsUnavailable() {
        val distribution = AppDistribution.valueOf(BuildConfig.APP_DISTRIBUTION)
        val roles = setOf(UserRole.STUDENT, UserRole.TEACHER, UserRole.TUTOR, UserRole.ADMIN, UserRole.SUPER_ADMIN)
        val available = RoleExperienceResolver.available(roles, distribution)
        assertTrue(available.isNotEmpty())
        assertTrue(available.all(distribution::allows))
    }
}
