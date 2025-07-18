package io.github.rastiehaiev.handlers.callback

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.handlers.LanguageAssistanceResult
import io.github.rastiehaiev.handlers.LanguageAssistant
import io.github.rastiehaiev.handlers.refreshResponseInlineKeyboard
import io.github.rastiehaiev.handlers.retrieveDictionary
import io.github.rastiehaiev.repository.FileVocabularyRepository

@Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE")
class RefreshBotResponseHandler(
    private val repository: FileVocabularyRepository,
    private val languageAssistant: LanguageAssistant,
) : UserCallbackQueryHandler {
    override fun isApplicable(chatId: ChatId.Id, callbackQuery: CallbackQuery): Boolean =
        callbackQuery.data == "refresh"

    override fun Bot.handle(chatId: ChatId.Id, callbackQuery: CallbackQuery) {
        val message = callbackQuery.message ?: return
        val userInput = message.replyToMessage?.text ?: return
        val userId = callbackQuery.from.id
        val ownerUserId = message.replyToMessage?.from?.id ?: return
        if (userId != ownerUserId) {
            answerCallbackQuery(
                callbackQueryId = callbackQuery.id,
                text = "Лапуль, ти можеш оновлювати відповіді бота тільки на свої повідомлення.",
                showAlert = true,
            )
        } else {
            val dictionary = message.text?.retrieveDictionary()

            dictionary?.also { repository.delete(userId, it) }
            languageAssistant.handle(chatId.id, userId, userInput)
                .onSuccess { result ->
                    if (result is LanguageAssistanceResult.Success) {
                        editMessageText(
                            chatId,
                            messageId = message.messageId,
                            text = result.responseText,
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = refreshResponseInlineKeyboard(
                                dictionaryUpdated = result.dictionaryItemsCount > 0,
                            ),
                        )
                    }
                }
        }
    }
}
