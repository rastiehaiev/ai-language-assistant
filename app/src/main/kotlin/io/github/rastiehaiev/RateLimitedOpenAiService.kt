package io.github.rastiehaiev

import io.github.rastiehaiev.service.OpenAiService
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RateLimitedOpenAiService(private val delegate: OpenAiService) {
    private val rateLimits = ConcurrentHashMap<String, AtomicInteger>()

    fun fixUserInputInGroup(chatId: Long, userInput: String) =
        rateLimit(chatId)
            .map { delegate.fixUserInputInGroup(userInput) }

    fun fixUserInputInPersonalChat(chatId: Long, userInput: String) =
        rateLimit(chatId)
            .map { delegate.fixUserInputInPersonalChat(userInput) }

    private fun rateLimit(chatId: Long): Result<Unit> {
        val key = "${LocalDate.now()}:$chatId"
        val currentUsage = rateLimits.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        return if (currentUsage >= 100 && chatId != 228089372L) {
            Result.failure(IllegalStateException("Too many requests!"))
        } else {
            Result.success(Unit)
        }
    }
}
