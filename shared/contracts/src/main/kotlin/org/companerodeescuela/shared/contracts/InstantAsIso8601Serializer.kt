package org.companerodeescuela.shared.contracts

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [Instant] as an ISO-8601 UTC string (`2026-01-31T12:00:00Z`).
 *
 * `java.time` is available on every supported target (JVM 17 and Android
 * minSdk 26), so no extra date/time dependency is required just for this.
 */
object InstantAsIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
