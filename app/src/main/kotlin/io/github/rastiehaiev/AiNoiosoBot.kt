package io.github.rastiehaiev

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.rastiehaiev.client.OpenAiClient
import io.github.rastiehaiev.handlers.message.isPersonalChat
import io.github.rastiehaiev.service.AiLanguageAssistantService
import io.github.rastiehaiev.utils.getEnv
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

fun main() {
    val openaiApiKey = getEnv("AI_NOIOSO_OPENAI_API_KEY")
    val telegramBotToken = getEnv("AI_NOIOSO_TELEGRAM_BOT_TOKEN")

    val rateLimiter = RateLimiter(maxCallsPerMinute = 5)
    val openAiClient = OpenAiClient(apiKey = openaiApiKey)
    val languageAssistantService = AiLanguageAssistantService(openAiClient)

    val bot = bot {
        token = telegramBotToken
        logLevel = LogLevel.All(networkLogLevel = LogLevel.Network.Body)

        dispatch {
            message {
                val fromId = message.from?.id
                if (fromId == 7860239705L || fromId == 228089372L) {
                    val input = message.text?.trim()?.takeIf { it.length < 200 && it.length > 10 } ?: return@message
                    if (input.contains("@AiNoiosoBot") && !message.isPersonalChat()) {
                        val responseText = if (rateLimiter.isAllowed()) {
                            languageAssistantService.annoyFor(input)
                        } else {
                            "Too many requests. Try again later."
                        }
                        if (!responseText.isNullOrEmpty()) {
                            bot.sendMessage(
                                chatId = ChatId.fromId(message.chat.id),
                                text = responseText,
                                replyToMessageId = message.messageId,
                                parseMode = ParseMode.MARKDOWN,
                            )
                        }
                    }
                }
            }
        }
    }

    bot.startPolling()
}

private class RateLimiter(private val maxCallsPerMinute: Int) {
    private val callTimestamps = ConcurrentLinkedQueue<Long>()

    fun isAllowed(): Boolean {
        val now = Instant.now().epochSecond

        while (callTimestamps.isNotEmpty() && now - callTimestamps.peek() >= 60) {
            callTimestamps.poll()
        }

        return if (callTimestamps.size < maxCallsPerMinute) {
            callTimestamps.add(now)
            true
        } else {
            false
        }
    }
}
