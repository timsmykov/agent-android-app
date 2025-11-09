package com.example.aichat.di

import android.content.Context
import androidx.room.Room
import com.example.aichat.BuildConfig
import com.example.aichat.core.audio.AudioAnalyzer
import com.example.aichat.core.audio.VoiceRecorder
import com.example.aichat.data.api.ApiService
import com.example.aichat.data.local.AppDatabase
import com.example.aichat.data.local.dao.ConversationDao
import com.example.aichat.data.local.dao.MessageDao
import com.example.aichat.data.repo.ConversationRepositoryImpl
import com.example.aichat.data.repo.WebhookRepositoryImpl
import com.example.aichat.data.voice.ParakeetTranscriber
import com.example.aichat.domain.repo.ConversationRepository
import com.example.aichat.domain.repo.WebhookRepository
import com.example.aichat.domain.usecase.SendMessageUseCase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"

    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logger)
        }

        builder.addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json")

            if (original.body != null && original.header("Content-Type") == null) {
                requestBuilder.header("Content-Type", "application/json")
            }

            chain.proceed(requestBuilder.build())
        })

        return builder.build()
    }

    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    fun provideRetrofit(json: Json, okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideWebhookRepository(
        apiService: ApiService,
        json: Json
    ): WebhookRepository = WebhookRepositoryImpl(apiService, json)

    @Provides
    @Singleton
    fun provideConversationRepository(
        conversationDao: ConversationDao,
        messageDao: MessageDao,
        json: Json
    ): ConversationRepository = ConversationRepositoryImpl(conversationDao, messageDao, json)

    @Provides
    @Singleton
    fun provideSendMessageUseCase(
        repository: WebhookRepository
    ): SendMessageUseCase = SendMessageUseCase(repository)

    @Provides
    @Singleton
    fun provideAudioAnalyzer(): AudioAnalyzer = AudioAnalyzer()

    @Provides
    @Singleton
    fun provideVoiceRecorder(): VoiceRecorder = VoiceRecorder()

    @Provides
    @Singleton
    fun provideParakeetTranscriber(
        okHttpClient: OkHttpClient,
        json: Json,
        ioDispatcher: CoroutineDispatcher
    ): ParakeetTranscriber = ParakeetTranscriber(okHttpClient, json, ioDispatcher)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "conversation_history.db"
    ).build()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
