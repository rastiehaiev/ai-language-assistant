package io.github.rastiehaiev.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class OpenAiClient(private val apiKey: String) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
    }

    fun ask(
        systemAdvice: String,
        userPrompt: String,
        model: String = "gpt-4o",
    ): Result<String?> = runBlocking {
        runCatching {
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model,
                        messages = listOf(
                            ChatMessage("system", systemAdvice),
                            ChatMessage("user", userPrompt),
                        )
                    )
                )
            }
            logger.info("Response body: '${response.body<String>()}'")
            val responseBody = response.body<ChatResponse>()
            responseBody.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotBlank() && it != "-" }
        }
    }
}

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatResponseChoice(val message: ChatMessage)

@Serializable
data class ChatResponse(val choices: List<ChatResponseChoice>? = null)
