package io.github.rastiehaiev.service

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AiTextAnalyzationServiceRateLimited(private val delegate: AiTextAnalyzationService) {
    private val rateLimits = ConcurrentHashMap<String, AtomicInteger>()

    fun analyze(chatId: Long, userInput: String) = rateLimit(chatId).map { delegate.analyze(userInput) }

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
