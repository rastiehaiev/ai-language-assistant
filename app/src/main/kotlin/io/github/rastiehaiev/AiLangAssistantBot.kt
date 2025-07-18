@file:Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE")

package io.github.rastiehaiev

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.rastiehaiev.client.MochiClient
import io.github.rastiehaiev.client.OpenAiClient
import io.github.rastiehaiev.handlers.LanguageAssistant
import io.github.rastiehaiev.handlers.callback.ManageDictionaryHandler
import io.github.rastiehaiev.handlers.callback.RefreshBotResponseHandler
import io.github.rastiehaiev.handlers.message.AddToDictionaryHandler
import io.github.rastiehaiev.handlers.message.DefaultUserInputHandler
import io.github.rastiehaiev.handlers.message.ListDictionaryEntriesHandler
import io.github.rastiehaiev.handlers.message.PushDictionaryToMochiCardsHandler
import io.github.rastiehaiev.handlers.message.ReviewDictionaryHandler
import io.github.rastiehaiev.repository.BotResponsesHistory
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantService
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited
import io.github.rastiehaiev.service.MochiService
import io.github.rastiehaiev.utils.getEnv

fun main() {
    val persistenceDir = getEnv("AI_LANG_ASSISTANT_PERSISTENCE_DIR")
    val openaiApiKey = getEnv("AI_LANG_ASSISTANT_OPENAI_API_KEY")
    val mochiApiKey = getEnv("AI_LANG_ASSISTANT_MOCHI_API_KEY")
    val telegramBotToken = getEnv("AI_LANG_ASSISTANT_TELEGRAM_BOT_TOKEN")

    val botResponsesHistory = BotResponsesHistory()
    val repository = FileVocabularyRepository(directoryPath = persistenceDir)
    val openAiClient = OpenAiClient(apiKey = openaiApiKey)
    val languageAssistantService = AiLanguageAssistantServiceRateLimited(AiLanguageAssistantService(openAiClient))
    val languageAssistant = LanguageAssistant(repository, botResponsesHistory, languageAssistantService)

    val mochiClient = MochiClient(mochiApiKey)
    val mochiService = MochiService(mochiClient, repository)

    val defaultUserInputHandler = DefaultUserInputHandler(languageAssistant)
    val userMessageHandlers = listOf(
        PushDictionaryToMochiCardsHandler(mochiService),
        ReviewDictionaryHandler(repository),
        ListDictionaryEntriesHandler(repository),
        AddToDictionaryHandler(repository, languageAssistantService),
    )

    val userCallbackQueryHandlers = listOf(
        ManageDictionaryHandler(repository),
        RefreshBotResponseHandler(repository, languageAssistant, botResponsesHistory),
    )

    val bot = bot {
        token = telegramBotToken
        logLevel = LogLevel.All(networkLogLevel = LogLevel.Network.Body)

        dispatch {
            message {
                val input = message.text?.trim() ?: return@message

                val handler = userMessageHandlers
                    .firstOrNull { it.isApplicable(message, input) }
                    ?: defaultUserInputHandler

                with(handler) { bot.handle(message, input) }
            }
            callbackQuery {
                val chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) } ?: return@callbackQuery

                userCallbackQueryHandlers
                    .firstOrNull { it.isApplicable(chatId, callbackQuery) }
                    ?.apply {
                        bot.handle(chatId, callbackQuery)
                    }
            }
        }
    }

    bot.startPolling()
}
