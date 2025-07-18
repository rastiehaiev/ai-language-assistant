package io.github.rastiehaiev.handlers.callback

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.handlers.LanguageAssistanceResult
import io.github.rastiehaiev.handlers.LanguageAssistant
import io.github.rastiehaiev.handlers.refreshResponseInlineKeyboard
import io.github.rastiehaiev.repository.BotResponsesHistory
import io.github.rastiehaiev.repository.FileVocabularyRepository

@Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE")
class RefreshBotResponseHandler(
    private val repository: FileVocabularyRepository,
    private val languageAssistant: LanguageAssistant,
    private val botResponsesHistory: BotResponsesHistory,
) : UserCallbackQueryHandler {

    override fun isApplicable(chatId: ChatId.Id, callbackQuery: CallbackQuery): Boolean =
        callbackQuery.data.startsWith("refresh:")

    override fun Bot.handle(chatId: ChatId.Id, callbackQuery: CallbackQuery) {
        val messageId = callbackQuery.message?.messageId ?: return
        val sourceMessageId = callbackQuery.data.removePrefix("refresh:").toLongOrNull()
        if (sourceMessageId != null) {
            val userId = callbackQuery.from.id
            val ownerUserId = callbackQuery.message?.replyToMessage?.from?.id ?: return
            if (userId != ownerUserId) {
                answerCallbackQuery(
                    callbackQueryId = callbackQuery.id,
                    text = "Лапуль, ти можеш оновлювати відповіді бота тільки на свої повідомлення.",
                    showAlert = true,
                )
            } else {
                val savedResponse = botResponsesHistory.find(chatId.id, userId, sourceMessageId)
                if (savedResponse == null) {
                    answerCallbackQuery(
                        callbackQueryId = callbackQuery.id,
                        text = "Цю відповідь вже не можна оновити.",
                        showAlert = true,
                    )

                    editMessageReplyMarkup(
                        chatId,
                        messageId = messageId,
                        replyMarkup = InlineKeyboardMarkup.create(),
                    )
                } else {
                    val (userInput, userInputAnalyzed) = savedResponse
                    if (userInputAnalyzed.words != null) {
                        repository.delete(userId, userInputAnalyzed.words)
                    }
                    languageAssistant.handle(chatId.id, userId, sourceMessageId, userInput)
                        .onSuccess { result ->
                            if (result is LanguageAssistanceResult.Success) {
                                editMessageText(
                                    chatId,
                                    messageId = messageId,
                                    text = result.responseText,
                                    parseMode = ParseMode.MARKDOWN,
                                    replyMarkup = refreshResponseInlineKeyboard(sourceMessageId),
                                )
                            }
                        }
                }
            }
        }
    }
}
