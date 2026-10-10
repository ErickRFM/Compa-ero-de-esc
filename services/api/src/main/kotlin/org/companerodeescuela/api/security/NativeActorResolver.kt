package org.companerodeescuela.api.security

import com.auth0.jwt.interfaces.Payload
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.identity.IdentityProvider

/** Native management never resolves an institutional or unknown session by matching a user ID. */
class NativeActorResolver(private val accounts:PlatformAccountRepository,private val sessions:RefreshSessionRepository,
    private val tokens:AuthTokenService,identity:IdentityProvider) {
    private val authority=PlatformSessionAuthority(accounts,identity)
    suspend fun resolve(payload:Payload):PlatformAccount {
        val sessionId=tokens.sessionIdFrom(payload) ?: throw ApiException.Unauthorized()
        val session=authenticationDependency { sessions.find(sessionId) } ?: throw ApiException.Unauthorized()
        if(session.identitySource!=SessionIdentitySource.NATIVE) throw ApiException.Forbidden("A native platform identity is required")
        if(!authority.permits(payload,session,tokens)) throw ApiException.Unauthorized()
        val actor=authenticationDependency { accounts.findById(session.user.id) } ?: throw ApiException.Unauthorized()
        val claimed=tokens.userFrom(payload)
        if(!actor.permitsSession || actor.authRevision!=claimed.authRevision || actor.roles!=claimed.roles ||
            actor.accountStatus!=claimed.accountStatus || actor.institutionId!=claimed.institutionId) throw ApiException.Unauthorized()
        return actor
    }
}
