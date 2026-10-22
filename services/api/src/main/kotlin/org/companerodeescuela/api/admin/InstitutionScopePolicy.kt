package org.companerodeescuela.api.admin

import java.time.Clock
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

/** A JWT role alone never supplies a tenant or a delegated native capability. */
class InstitutionScopePolicy(private val accounts:PlatformAccountRepository,private val clock:Clock=Clock.systemUTC()) {
    suspend fun live(actor:UserSummary):PlatformAccount {
        val current=authenticationDependency { accounts.findById(actor.id) } ?: throw ApiException.Unauthorized()
        if(!actor.active || !current.permitsSession || current.accountStatus!=AccountStatus.ACTIVE ||
            current.authRevision!=actor.authRevision || current.roles!=actor.roles || current.accountStatus!=actor.accountStatus ||
            current.institutionId!=actor.institutionId) throw ApiException.Unauthorized()
        if(current.emailVerifiedAt==null || current.emailVerifiedAt>clock.instant()) throw ApiException.Forbidden("Verified native identity is required")
        return current
    }
    suspend fun require(actor:UserSummary,capability:AdminCapability,institutionId:String):PlatformAccount {
        val current=live(actor)
        if(institutionId.isBlank() || institutionId!=institutionId.trim() || institutionId.length>128 || institutionId.any(Char::isISOControl)) throw ApiException.Forbidden("Actual institution scope is required")
        if(UserRole.SUPER_ADMIN in current.roles) return current
        if((UserRole.ADMIN !in current.roles && UserRole.PRINCIPAL !in current.roles) || current.institutionId!=institutionId || capability !in current.adminCapabilities) throw ApiException.Forbidden("Institutional capability is not granted")
        return current
    }
}
