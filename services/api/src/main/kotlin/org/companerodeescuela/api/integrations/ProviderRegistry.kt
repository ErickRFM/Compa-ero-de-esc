package org.companerodeescuela.api.integrations

import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.events.EventsProvider
import org.companerodeescuela.api.integrations.identity.IdentityProvider
import org.companerodeescuela.api.integrations.library.LibraryProvider
import org.companerodeescuela.api.integrations.lms.LearningProvider
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockEventsProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.integrations.mock.MockLearningProvider
import org.companerodeescuela.api.integrations.mock.MockLibraryProvider
import org.companerodeescuela.api.integrations.mock.MockSyncProvider
import org.companerodeescuela.api.integrations.sync.SyncProvider
import org.slf4j.LoggerFactory

/**
 * Holds the adapter implementation selected for each integration.
 *
 * Swapping a mock for a real adapter is a single wiring change in
 * [Companion.wiringFor], never a change in a route, service or repository.
 *
 * The registry also enforces the safety rule that makes mocks safe to ship: a
 * non-local environment may not be wired with any [MockIntegrationProvider].
 */
class ProviderRegistry private constructor(
    val academic: AcademicProvider,
    val identity: IdentityProvider,
    val learning: LearningProvider,
    val library: LibraryProvider,
    val events: EventsProvider,
    val sync: List<SyncProvider>,
) : AutoCloseable {

    /**
     * Providers that return fabricated data. Must be empty in any environment
     * other than local.
     */
    fun mockProviders(): List<IntegrationProvider> =
        listOfNotNull(academic, identity, learning, library, events)
            .plus(sync)
            .filterIsInstance<MockIntegrationProvider>()

    override fun close() {
        log.info("Closing integration providers: {}", all().map { it.id })
    }

    fun all(): List<IntegrationProvider> =
        listOf(academic, identity, learning, library, events) + sync

    companion object {
        private val log = LoggerFactory.getLogger(ProviderRegistry::class.java)

        /**
         * Development wiring: every provider is mocked.
         *
         * This is the default so `.\gradlew :services:api:run` works on a fresh
         * clone with no external credentials.
         */
        fun mocks(): ProviderRegistry = ProviderRegistry(
            academic = MockAcademicProvider(),
            identity = MockIdentityProvider(),
            learning = MockLearningProvider(),
            library = MockLibraryProvider(),
            events = MockEventsProvider(),
            sync = listOf(MockSyncProvider()),
        )

        /**
         * Validates a registry against the environment it will run in.
         *
         * @throws IllegalStateException when a mock provider would be reachable
         *   outside local development. Failing at boot is intentional: a mock
         *   academic system serving real students is unrecoverable.
         */
        fun requireEnvironmentSatisfied(
            registry: ProviderRegistry,
            environment: Environment,
        ) {
            if (environment.isProduction || environment == Environment.STAGING) {
                val mocks = registry.mockProviders()
                check(mocks.isEmpty()) {
                    "Refusing to start in $environment with mock providers active: " +
                        mocks.joinToString { it.id }
                }
            }
        }
    }
}
