package org.companerodeescuela.feature.schedule

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PdfInputTest {
    @Test fun `oversized stream stops after one byte beyond limit`() {
        val input = CountingInput(ByteArray(512))
        assertNull(readPdfBytes(input, 64))
        assertEquals(65, input.bytesRead)
    }

    @Test fun `exact size and short reads preserve all bytes`() {
        val expected = ByteArray(64) { it.toByte() }
        val input = CountingInput(expected, chunkSize = 3)
        assertContentEquals(expected, readPdfBytes(input, 64))
        assertEquals(64, input.bytesRead)
    }

    @Test fun `empty PDF byte stream is retained for parser rejection`() {
        assertContentEquals(byteArrayOf(), readPdfBytes(ByteArrayInputStream(byteArrayOf()), 64))
    }

    @Test fun `zero limit reads only the overflow byte`() {
        val input = CountingInput(ByteArray(128))
        assertNull(readPdfBytes(input, 0))
        assertEquals(1, input.bytesRead)
    }

    @Test fun `intermittent zero progress cannot cause a spin or unbounded read`() {
        val input = object : InputStream() {
            val delegate = CountingInput(ByteArray(512))
            override fun read() = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
        }
        assertNull(readPdfBytes(input, 64))
        assertEquals(65, input.delegate.bytesRead)
    }

    @Test fun `cancellation check aborts consumption before another chunk`() {
        val input = CountingInput(ByteArray(512), chunkSize = 3)
        assertFailsWith<CancellationException> {
            readPdfBytes(input, 64) { if (input.bytesRead >= 3) throw CancellationException("Cancelled") }
        }
        assertEquals(3, input.bytesRead)
    }

    @Test fun `native reader cancellation propagates instead of enabling OCR`() = runTest {
        assertFailsWith<CancellationException> { readNativePdfOrNull<Unit> { throw CancellationException("Cancelled") } }
    }

    @Test fun `unsupported native PDF still permits existing OCR fallback`() = runTest {
        assertNull(readNativePdfOrNull<Unit> { throw IOException("Unsupported native PDF") })
        assertEquals("native text", readNativePdfOrNull { "native text" })
    }

    private class CountingInput(bytes: ByteArray, private val chunkSize: Int = Int.MAX_VALUE) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var bytesRead = 0
            private set
        override fun read(): Int = delegate.read().also { if (it != -1) bytesRead++ }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, minOf(length, chunkSize)).also { if (it > 0) bytesRead += it }
    }
}
