package org.companerodeescuela.api.academic.groups

internal fun AcademicGroupRecord.exactScopeRetry(expectedRevision:Long,change:AcademicGroupScopeChange):Boolean = scopeAudit.any {
    it.operationId==change.operationId && it.actorId==change.actorId && it.fromRevision==expectedRevision &&
        it.institutionAfter==change.institutionId && it.periodAfter==change.periodId && it.startsAt==change.startsAt && it.endsAt==change.endsAt && it.payloadDigest==change.payloadDigest
}
internal fun AcademicGroupRecord.applyScopeChange(expectedRevision:Long,change:AcademicGroupScopeChange):AcademicGroupRecord? {
    if(expectedRevision<0 || scopeRevision!=expectedRevision || scopeRevision==Long.MAX_VALUE || scopeAudit.size>=1000 ||
        scopeAudit.any { it.operationId==change.operationId } || (institutionId!=null && institutionId!=change.institutionId) ||
        change.startsAt>=change.endsAt || change.startsAt.epochSecond<0 || change.endsAt.epochSecond>4102444800L ||
        !Regex("[a-f0-9]{64}").matches(change.payloadDigest)) return null
    for(value in listOf(change.operationId,change.actorId,change.institutionId,change.periodId))
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) return null
    val event=AcademicGroupScopeAudit(change.operationId,change.actorId,scopeRevision,scopeRevision+1,institutionId,change.institutionId,
        periodId,change.periodId,change.startsAt,change.endsAt,change.occurredAt,change.payloadDigest)
    return copy(institutionId=change.institutionId,periodId=change.periodId,startsAt=change.startsAt,endsAt=change.endsAt,scopeRevision=scopeRevision+1,scopeAudit=scopeAudit+event)
}
