package org.companerodeescuela.api.presence

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.SchoolPresenceIdentitySource

interface SchoolPresenceRepository {
    suspend fun save(value: SchoolPresenceResponse): SchoolPresenceResponse
    suspend fun findById(id: String): SchoolPresenceResponse?
    suspend fun findActiveForStudent(studentId: String, nowEpochSeconds: Long): SchoolPresenceResponse?
    suspend fun findActiveForNativeStudent(studentId:String,institutionId:String,nowEpochSeconds:Long):SchoolPresenceResponse? = null
    suspend fun createNative(value:SchoolPresenceResponse,nowEpochSeconds:Long):SchoolPresenceResponse? = null
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
            .filter { it.studentId == studentId && it.identitySource!=SchoolPresenceIdentitySource.NATIVE }
            .filter { it.status == SchoolPresenceStatus.ACTIVE }
            .filter { it.expiresAtEpochSeconds > nowEpochSeconds }
            .maxByOrNull { it.startedAtEpochSeconds }
    }

    override suspend fun findActiveForNativeStudent(studentId:String,institutionId:String,nowEpochSeconds:Long):SchoolPresenceResponse? = mutex.withLock {
        records.values.filter { it.nativeScope(studentId,institutionId) && it.status==SchoolPresenceStatus.ACTIVE && it.expiresAtEpochSeconds>nowEpochSeconds }.maxByOrNull { it.startedAtEpochSeconds }
    }
    override suspend fun createNative(value:SchoolPresenceResponse,nowEpochSeconds:Long):SchoolPresenceResponse? = mutex.withLock {
        if(!value.validNativeCreation(nowEpochSeconds)) return@withLock null
        val institution=value.institutionId!!
        records[value.id]?.let { previous -> return@withLock previous.takeIf { it.nativeScope(value.studentId,institution) && it.operationId==value.operationId && it.status==SchoolPresenceStatus.ACTIVE && it.expiresAtEpochSeconds>nowEpochSeconds } }
        for((key,record) in records.toMap()) if(record.nativeScope(value.studentId,institution) && record.status==SchoolPresenceStatus.ACTIVE && record.expiresAtEpochSeconds<=nowEpochSeconds) records[key]=record.copy(status=SchoolPresenceStatus.EXPIRED)
        records.values.firstOrNull { it.nativeScope(value.studentId,institution) && it.status==SchoolPresenceStatus.ACTIVE && it.expiresAtEpochSeconds>nowEpochSeconds }?.let { return@withLock it }
        records[value.id]=value
        value
    }
    override suspend fun replace(value: SchoolPresenceResponse) {
        mutex.withLock { records[value.id] = value }
    }
}

internal fun SchoolPresenceResponse.nativeScope(student:String,institution:String)=studentId==student && institutionId==institution && identitySource==SchoolPresenceIdentitySource.NATIVE
internal fun SchoolPresenceResponse.validNativeCreation(now:Long)=identitySource==SchoolPresenceIdentitySource.NATIVE && !institutionId.isNullOrBlank() && !operationId.isNullOrBlank() &&
    status==SchoolPresenceStatus.ACTIVE && !networkVerified && startedAtEpochSeconds<=now && expiresAtEpochSeconds>now && closedAtEpochSeconds==null
