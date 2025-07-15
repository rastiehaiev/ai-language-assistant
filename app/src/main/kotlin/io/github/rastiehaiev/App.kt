@file:Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE")

package io.github.rastiehaiev

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.rastiehaiev.client.MochiClient
import io.github.rastiehaiev.client.OpenAiClient
import io.github.rastiehaiev.handlers.AddToDictionaryHandler
import io.github.rastiehaiev.handlers.DefaultUserInputHandler
import io.github.rastiehaiev.handlers.ListDictionaryEntriesHandler
import io.github.rastiehaiev.handlers.PushDictionaryToMochiCardsHandler
import io.github.rastiehaiev.handlers.ReviewDictionaryHandler
import io.github.rastiehaiev.handlers.toMarkup
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantService
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited
import io.github.rastiehaiev.service.MochiService
import java.util.*

private fun getEnv(name: String) = System.getenv(name) ?: error("Environment variable '$name' not set!")

fun main() {
    val persistenceDir = getEnv("AI_LANG_ASSISTANT_PERSISTENCE_DIR")
    val openaiApiKey = getEnv("AI_LANG_ASSISTANT_OPENAI_API_KEY")
    val mochiApiKey = getEnv("AI_LANG_ASSISTANT_MOCHI_API_KEY")
    val telegramBotToken = getEnv("AI_LANG_ASSISTANT_TELEGRAM_BOT_TOKEN")

    val repository = FileVocabularyRepository(directoryPath = persistenceDir)
    val openAiClient = OpenAiClient(apiKey = openaiApiKey)
    val languageAssistantService = AiLanguageAssistantServiceRateLimited(AiLanguageAssistantService(openAiClient))

    val mochiClient = MochiClient(mochiApiKey)
    val mochiService = MochiService(mochiClient, repository)

    val defaultUserInputHandler = DefaultUserInputHandler(repository, languageAssistantService)
    val userInputHandlers = listOf(
        PushDictionaryToMochiCardsHandler(mochiService),
        ReviewDictionaryHandler(repository),
        ListDictionaryEntriesHandler(repository),
        AddToDictionaryHandler(repository, languageAssistantService),
    )

    val bot = bot {
        token = telegramBotToken
        logLevel = LogLevel.All(networkLogLevel = LogLevel.Network.Body)

        dispatch {
            message {
                val input = message.text?.trim() ?: return@message

                val handler = userInputHandlers
                    .firstOrNull { it.isApplicable(message, input) }
                    ?: defaultUserInputHandler

                with(handler) { bot.handle(message, input) }
            }
            callbackQuery {
                val chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) } ?: return@callbackQuery
                val callbackData = callbackQuery.data

                fun String.toEntryId(label: String): UUID = this.removePrefix(label).let { UUID.fromString(it) }

                val nextEntry = if (callbackData.startsWith("review:delete:")) {
                    val entryId = callbackData.toEntryId("review:delete:")
                    repository.delete(chatId.id, entryId)
                } else if (callbackData.startsWith("review:next:")) {
                    val entryId = callbackData.toEntryId("review:next:")
                    repository.next(chatId.id, entryId)
                } else if (callbackData.startsWith("review:back:")) {
                    val entryId = callbackData.toEntryId("review:back:")
                    repository.back(chatId.id, entryId)
                } else {
                    null
                }

                if (nextEntry == null) {
                    val messageId = callbackQuery.message?.messageId
                    if (messageId != null) {
                        bot.deleteMessage(chatId, messageId)
                    }
                } else {
                    val messageId = callbackQuery.message?.messageId
                    if (messageId != null) {
                        with(nextEntry.toMarkup()) {
                            bot.editMessageText(
                                chatId,
                                messageId,
                                inlineMessageId = null,
                                text = messageText,
                                parseMode = ParseMode.MARKDOWN_V2,
                                replyMarkup = messageMarkup,
                            )
                        }
                    }
                }
            }
        }
    }

    bot.startPolling()
}
