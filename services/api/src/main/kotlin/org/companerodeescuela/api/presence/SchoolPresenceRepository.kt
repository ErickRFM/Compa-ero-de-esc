package org.companerodeescuela.api.presence

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus

interface SchoolPresenceRepository {
    suspend fun save(value: SchoolPresenceResponse): SchoolPresenceResponse
    suspend fun findById(id: String): SchoolPresenceResponse?
    suspend fun findActiveForStudent(studentId: String, nowEpochSeconds: Long): SchoolPresenceResponse?
    suspend fun replace(value: SchoolPresenceResponse)
}

class InMemorySchoolPresenceRepository : SchoolPresenceRepository {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, SchoolPresenceResponse>()

    override suspend fun save(value: SchoolPresenceResponse): SchoolPresenceResponse =
        mutex.withLock {
            records[value.id] = value
            value
        }

    override suspend fun findById(id: String): SchoolPresenceResponse? =
        mutex.withLock { records[id] }

    override suspend fun findActiveForStudent(
        studentId: String,
        nowEpochSeconds: Long,
    ): SchoolPresenceResponse? = mutex.withLock {
        records.values
            .asSequence()
            .filter { it.studentId == studentId }
            .filter { it.status == SchoolPresenceStatus.ACTIVE }
            .filter { it.expiresAtEpochSeconds > nowEpochSeconds }
            .maxByOrNull { it.startedAtEpochSeconds }
    }

    override suspend fun replace(value: SchoolPresenceResponse) {
        mutex.withLock { records[value.id] = value }
    }
}
