package org.companerodeescuela.feature.channel

import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelAcknowledgement
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.ChannelPost
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest

class ChannelRepository(
    private val client: HttpClient,
    private val tokenStore: SessionTokenStore,
    private val refreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(client, tokenStore),
) {
    suspend fun channels(): Outcome<List<ClassChannelSummary>> =
        authorized { token ->
            apiCall {
                client.get("channels") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ClassChannelSummary>>>()
            }.map { it.data }
        }

    suspend fun posts(channelId: String): Outcome<List<ChannelPost>> =
        authorized { token ->
            apiCall {
                client.get("channels/$channelId/posts") { bearerAuth(token) }
                    .requireBody<ApiResponse<List<ChannelPost>>>()
            }.map { it.data }
        }

    suspend fun publish(
        channelId: String,
        request: CreateChannelPostRequest,
    ): Outcome<ChannelPost> =
        authorized { token ->
            apiCall {
                client.post("channels/$channelId/posts") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.requireBody<ApiResponse<ChannelPost>>()
            }.map { it.data }
        }

    suspend fun acknowledge(
        channelId: String,
        postId: String,
        request: ChannelAcknowledgementRequest,
    ): Outcome<ChannelAcknowledgement> =
        authorized { token ->
            apiCall {
                client.put("channels/$channelId/posts/$postId/acknowledgement") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.requireBody<ApiResponse<ChannelAcknowledgement>>()
            }.map { it.data }
        }

    private suspend fun <T> authorized(
        block: suspend (String) -> Outcome<T>,
    ): Outcome<T> {
        val token = when (val result = refreshCoordinator.currentAccessToken()) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        return refreshCoordinator.execute(token, block)
    }
}
