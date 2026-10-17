package org.companerodeescuela.api.integrations.academic

import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicProvider
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SiiiRegistryWiringTest {
    @Test
    fun `explicit registry wiring preserves collaborators and rejects remaining production mocks`() {
        val existing = ProviderRegistry.mocks()
        val academic = SiiiAcademicProvider(URI("https://synthetic.example.test"), "synthetic_companion_token_0123456789abcdef")
        val configured =
            ProviderRegistry.configured(
                academic = academic,
                identity = existing.identity,
                learning = existing.learning,
                library = existing.library,
                events = existing.events,
                sync = existing.sync,
            )
        assertSame(academic, configured.academic)
        assertSame(existing.identity, configured.identity)
        assertSame(existing.learning, configured.learning)
        assertSame(existing.library, configured.library)
        assertSame(existing.events, configured.events)
        assertFailsWith<IllegalStateException> {
            ProviderRegistry.requireEnvironmentSatisfied(configured, Environment.PRODUCTION)
        }
    }
}
