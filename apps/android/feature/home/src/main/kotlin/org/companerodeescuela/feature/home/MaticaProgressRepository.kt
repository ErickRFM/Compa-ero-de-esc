package org.companerodeescuela.feature.home

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.network.requireUnit
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.MaticaProgressLinkRequest
import org.companerodeescuela.shared.contracts.MaticaProgressSummary

class MaticaProgressRepository
    @Inject
    constructor(
        private val client: HttpClient,
        private val refresh: SessionRefreshCoordinator,
    ) {
        suspend fun load(): Outcome<MaticaProgressSummary?> =
            protectedCall { token ->
                val result =
                    apiCall {
                        client
                            .get("integrations/matica/progress") {
                                bearerAuth(token)
                            }.requireBody<ApiResponse<MaticaProgressSummary>>()
                    }.map { it.data }
                when (result) {
                    is Outcome.Success -> Outcome.Success(result.value)
                    is Outcome.Failure ->
                        if ((result.error as? AppError.Http)?.status == 404) {
                            Outcome.Success(null)
                        } else {
                            result
                        }
                }
            }

        suspend fun link(code: String): Outcome<Unit> =
            protectedCall { token ->
                apiCall {
                    client
                        .post("integrations/matica/link") {
                            bearerAuth(token)
                            contentType(ContentType.Application.Json)
                            setBody(MaticaProgressLinkRequest(code.trim()))
                        }.requireBody<ApiResponse<Map<String, Boolean>>>()
                }.map { Unit }
            }

        suspend fun unlink(): Outcome<Unit> =
            protectedCall { token ->
                apiCall {
                    client
                        .delete("integrations/matica/link") {
                            bearerAuth(token)
                        }.requireUnit()
                }
            }

        private suspend fun <T> protectedCall(action: suspend (String) -> Outcome<T>): Outcome<T> =
            when (val current = refresh.currentAccessToken()) {
                is Outcome.Failure -> current
                is Outcome.Success -> refresh.execute(current.value, action)
            }
    }
