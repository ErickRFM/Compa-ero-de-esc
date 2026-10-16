package org.companerodeescuela.api.application

import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedWorkerLifecycleTest {
    @Test
    fun `worker is not started when integration is not configured`() =
        testApplication {
            val starts = AtomicInteger()
            var job: Job? = null
            application {
                job =
                    launchManagedWorker(enabled = false, context = Dispatchers.Unconfined) {
                        starts.incrementAndGet()
                        awaitCancellation()
                    }
            }

            assertNull(job)
            assertEquals(0, starts.get())
        }

    @Test
    fun `worker is cancelled when application stops`() {
        val starts = AtomicInteger()
        lateinit var job: Job

        testApplication {
            application {
                job =
                    launchManagedWorker(enabled = true, context = Dispatchers.Unconfined) {
                        starts.incrementAndGet()
                        awaitCancellation()
                    }!!
            }

            assertEquals(404, client.get("/health").status.value)
            assertEquals(1, starts.get())
            assertTrue(job.isActive)
        }

        assertTrue(job.isCancelled)
    }
}
