package com.example.aichat.data.repo

import com.example.aichat.BuildConfig
import com.example.aichat.core.Result
import com.example.aichat.data.api.ApiService
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.WebhookPayload
import com.example.aichat.domain.model.WebhookResponse
import com.example.aichat.domain.repo.WebhookRepository
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber

class WebhookRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val json: Json
) : WebhookRepository {

    override suspend fun send(message: ChatMessage, sessionId: String): Result<WebhookResponse> {
        val payload = WebhookPayload.fromChatMessage(message, sessionId)
        val url = if (BuildConfig.N8N_MODE == "prod") BuildConfig.N8N_PROD_URL else BuildConfig.N8N_TEST_URL

        return try {
            val response = apiService.postMessage(url, payload)
            val httpCode = response.code()
            if (response.isSuccessful) {
                val body = response.body()
                val parsed = body?.let { mapJson(it, httpCode) }
                val result = parsed ?: WebhookResponse(
                    ok = true,
                    status = "HTTP $httpCode",
                    message = "OK",
                    httpCode = httpCode
                )
                Result.Success(result)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                Timber.w("Webhook error ${response.code()} $errorBody")
                Result.Error(HttpException(response.code(), errorBody))
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Webhook send failed")
            Result.Error(ex)
        }
    }

    private fun mapJson(element: JsonElement, code: Int): WebhookResponse? = try {
        val dto = json.decodeFromJsonElement(WebhookResponse.serializer(), element)
        dto.copy(httpCode = code)
    } catch (ex: SerializationException) {
        Timber.w(ex, "Invalid webhook JSON")
        null
    }

    class HttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")
}
