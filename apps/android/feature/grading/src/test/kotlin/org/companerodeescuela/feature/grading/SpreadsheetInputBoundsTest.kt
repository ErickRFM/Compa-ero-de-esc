package org.companerodeescuela.feature.grading

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class SpreadsheetInputBoundsTest {
    @Test
    fun `optional provider metadata failure allows filename fallback but cancellation propagates`() {
        assertNull(optionalGradeImportMetadata { throw UnsupportedOperationException("no display name") })
        assertNull(optionalGradeImportMetadata { throw IllegalStateException("provider unavailable") })
        val cancelled = CancellationException("screen closed")
        assertSame(cancelled, assertFailsWith<CancellationException> { optionalGradeImportMetadata { throw cancelled } })
    }

    @Test
    fun `bounded input preserves an ordinary CSV`() {
        val csv = "Matricula,Examen\nA001,9".toByteArray()
        assertContentEquals(csv, SpreadsheetReader.readBoundedInput(ByteArrayInputStream(csv)))
    }

    @Test
    fun `oversized provider is rejected after five MiB plus one byte`() {
        val limit = 5 * 1024 * 1024
        var readBytes = 0
        val provider =
            object : InputStream() {
                override fun read(): Int {
                    readBytes++
                    return 'A'.code
                }
            }
        assertFailsWith<IOException> { SpreadsheetReader.readBoundedInput(provider) }
        assertEquals(limit + 1, readBytes)
    }

    @Test
    fun `provider failure is preserved for the caller instead of importing partial data`() {
        val provider =
            object : InputStream() {
                override fun read(): Int = throw IOException("provider unavailable")
            }
        assertEquals(
            "provider unavailable",
            assertFailsWith<IOException> { SpreadsheetReader.readBoundedInput(provider) }.message,
        )
    }
}
