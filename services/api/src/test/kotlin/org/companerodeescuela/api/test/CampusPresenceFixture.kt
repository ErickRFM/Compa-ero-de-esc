package org.companerodeescuela.api.test

import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.UserSummary
import java.security.MessageDigest
import java.time.Clock

/** Synthetic QR and school network only; no device or production evidence. */
suspend fun campusPresenceFixture(
    accounts: PlatformAccountRepository,
    clock: Clock,
    institutionId: String,
    actors: List<UserSummary>,
): SchoolPresenceService {
    val entry = "synthetic-entry-$institutionId"
    val hash = MessageDigest.getInstance("SHA-256").digest(entry.toByteArray()).joinToString("") { "%02x".format(it) }
    val service =
        SchoolPresenceService(
            InMemorySchoolPresenceRepository(),
            SchoolPresencePolicy(hash, setOf("School"), emptySet(), institutionId = institutionId),
            clock = clock,
            accounts = accounts,
        )
    for (actor in actors) {
        service.start(
            actor.id,
            StartSchoolPresenceRequest("entry-${actor.id}", entry, SchoolNetworkEvidence(ssid = "School"), clock.instant().epochSecond),
            actor,
        )
    }
    return service
}
