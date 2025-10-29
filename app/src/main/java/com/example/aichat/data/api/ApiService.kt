package com.example.aichat.data.api

import com.example.aichat.domain.model.WebhookPayload
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    @POST
    suspend fun postMessage(
        @Url url: String,
        @Body payload: WebhookPayload
    ): Response<JsonElement>
}
