package io.github.rastiehaiev.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
data class Card(val id: String, val content: String)

@Serializable
data class CardRequest(
    @SerialName("deck-id")
    val deckId: String,
    val content: String,
    @SerialName("review-reverse?")
    val reviewReverse: Boolean = true,
)

@Serializable
data class CardsResponse(val docs: List<Card>, val bookmark: String? = null)

class MochiClient(private val apiKey: String) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }
        defaultRequest {
            val authorizationHeaderValue = "Basic " + Base64.getEncoder().encodeToString("$apiKey:".toByteArray())
            header(HttpHeaders.Authorization, authorizationHeaderValue)
        }
    }

    fun save(deckId: String, words: Map<String, List<String>>): Result<Int> = runBlocking {
        runCatching {
            val existingCards = findAll(deckId)
            words.entries.flatMap { (key, values) -> values.map { key to it } }
                .associateBy { (key, value) -> "# $key\n---\n$value" }
                .minus(existingCards)
                .onEach { (cardContent) -> saveOne(deckId, cardContent) }
                .map { (_, value) -> value }
                .count()
        }
    }

    private suspend fun saveOne(deckId: String, cardContent: String) {
        val response = client.post("https://app.mochi.cards/api/cards") {
            contentType(ContentType.Application.Json)
            setBody(CardRequest(deckId, cardContent))
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Could not save card. Response:\n${response.body<String>()}")
        }
    }

    private suspend fun findAll(deckId: String): Set<String> {
        val allCards = mutableSetOf<String>()
        var bookmark: String? = null
        var cards: Set<String>
        do {
            val response = client.get("https://app.mochi.cards/api/cards") {
                parameter("deck-id", deckId)
                parameter("limit", 100)
                if (bookmark != null) {
                    parameter("bookmark", bookmark)
                }
                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }

            val bodyAsString = response.body<String>()

            val cardsResponse = try {
                json.decodeFromString<CardsResponse>(bodyAsString)
            } catch (e: Exception) {
                log.error("Failed to decode response.\n$bodyAsString", e)
                throw e
            }

            cards = cardsResponse.docs.map { it.content.trim() }.toSet()

            allCards.addAll(cards)
            bookmark = cardsResponse.bookmark
        } while (bookmark != null && cards.isNotEmpty())
        return allCards
    }
}
