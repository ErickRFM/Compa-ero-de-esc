package org.companerodeescuela.api.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC6238 moving counter with bounded algorithms; production authenticator codes use six digits. */
internal object Totp {
    private const val ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun code(secret: ByteArray, epochSeconds: Long, digits: Int = 6, algorithm: String = "HmacSHA1"): String {
        require(secret.size in 20..64 && epochSeconds >= 0 && digits in 6..8)
        require(algorithm in setOf("HmacSHA1","HmacSHA256","HmacSHA512"))
        val digest=Mac.getInstance(algorithm).run {
            init(SecretKeySpec(secret,algorithm))
            doFinal(ByteBuffer.allocate(8).putLong(epochSeconds/30).array())
        }
        val offset=digest.last().toInt() and 15
        val binary=((digest[offset].toInt() and 127) shl 24) or
            ((digest[offset+1].toInt() and 255) shl 16) or
            ((digest[offset+2].toInt() and 255) shl 8) or (digest[offset+3].toInt() and 255)
        val divisor=when(digits) { 6 -> 1_000_000; 7 -> 10_000_000; else -> 100_000_000 }
        return (binary%divisor).toString().padStart(digits,'0')
    }
    fun matchingStep(secret: ByteArray, otp: String, now: Instant): Long? {
        if (!Regex("[0-9]{6}").matches(otp) || now.epochSecond < 0) return null
        val current=now.epochSecond/30;var matched:Long?=null
        for(step in (current-1)..(current+1)) {
            if (step>=0 && MessageDigest.isEqual(code(secret,step*30).toByteArray(Charsets.US_ASCII),otp.toByteArray(Charsets.US_ASCII))) matched=step
        }
        return matched
    }
    fun encodeSecret(secret: ByteArray): String {
        require(secret.size in 20..64)
        var buffer=0;var bits=0
        return buildString {
            for(byte in secret) {
                buffer=(buffer shl 8) or (byte.toInt() and 255);bits+=8
                while(bits>=5) { bits-=5;append(ALPHABET[(buffer ushr bits) and 31]) }
            }
            if(bits>0) append(ALPHABET[(buffer shl (5-bits)) and 31])
        }
    }
    fun decodeSecret(encoded: String): ByteArray {
        require(encoded.length in 32..103 && encoded.all { it in ALPHABET })
        val bytes=ArrayList<Byte>();var buffer=0;var bits=0
        for(char in encoded) {
            buffer=(buffer shl 5) or ALPHABET.indexOf(char);bits+=5
            if(bits>=8) { bits-=8;bytes.add(((buffer ushr bits) and 255).toByte()) }
        }
        val decoded=bytes.toByteArray()
        require(encodeSecret(decoded)==encoded) { "MFA secret encoding is invalid" }
        return decoded
    }
}
