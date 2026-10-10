package org.companerodeescuela.api.security

import java.time.Instant
import kotlin.test.*

class TotpTest {
    @Test fun `published RFC6238 SHA1 vectors including timestamps beyond2038`() {
        val key="12345678901234567890".toByteArray()
        val vectors=listOf(59L to "94287082",1111111109L to "07081804",1111111111L to "14050471",1234567890L to "89005924",2000000000L to "69279037",20000000000L to "65353130")
        for((time,expected) in vectors) assertEquals(expected,Totp.code(key,time,8))
    }
    @Test fun `published SHA256 and SHA512 vectors`() {
        val sha256="12345678901234567890123456789012".toByteArray()
        val sha512="1234567890123456789012345678901234567890123456789012345678901234".toByteArray()
        assertEquals("46119246",Totp.code(sha256,59,8,"HmacSHA256"))
        assertEquals("90693936",Totp.code(sha512,59,8,"HmacSHA512"))
        assertEquals("77737706",Totp.code(sha256,20000000000,8,"HmacSHA256"))
        assertEquals("47863826",Totp.code(sha512,20000000000,8,"HmacSHA512"))
    }
    @Test fun `six-digit verifier accepts bounded adjacent step and rejects stale or malformed codes`() {
        val key="12345678901234567890".toByteArray();val now=Instant.ofEpochSecond(1234567890)
        assertEquals(41152263L,Totp.matchingStep(key,"005924",now))
        assertEquals(41152262L,Totp.matchingStep(key,Totp.code(key,1234567860),now))
        assertNull(Totp.matchingStep(key,Totp.code(key,1234567800),now))
        for(code in listOf("89005924"," 005924","abcdef","１２３４５６")) assertNull(Totp.matchingStep(key,code,now))
    }
    @Test fun `authenticator base32 roundtrip preserves random binary without accepting ambiguous encoding`() {
        val bytes=ByteArray(20) { (it*17).toByte() }
        assertContentEquals(bytes,Totp.decodeSecret(Totp.encodeSecret(bytes)))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",Totp.encodeSecret("12345678901234567890".toByteArray()))
        assertFailsWith<IllegalArgumentException> { Totp.decodeSecret("bad!seed") }
    }
    @Test fun `invalid secret clock algorithm or digits cannot produce a code`() {
        assertFailsWith<IllegalArgumentException> { Totp.code(byteArrayOf(1),59) }
        assertFailsWith<IllegalArgumentException> { Totp.code(ByteArray(20),-1) }
        assertFailsWith<IllegalArgumentException> { Totp.code(ByteArray(20),59,12) }
        assertFailsWith<IllegalArgumentException> { Totp.code(ByteArray(20),59,6,"HmacMD5") }
    }
}
