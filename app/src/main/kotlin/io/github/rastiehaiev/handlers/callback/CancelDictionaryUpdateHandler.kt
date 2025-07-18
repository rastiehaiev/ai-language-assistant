package io.github.rastiehaiev.handlers.callback

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.handlers.LanguageAssistant
import io.github.rastiehaiev.handlers.refreshResponseInlineKeyboard
import io.github.rastiehaiev.handlers.retrieveDictionary
import io.github.rastiehaiev.repository.FileVocabularyRepository

class CancelDictionaryUpdateHandler(
    private val repository: FileVocabularyRepository,
) : UserCallbackQueryHandler {
    override fun isApplicable(
        chatId: ChatId.Id,
        callbackQuery: CallbackQuery,
    ): Boolean = callbackQuery.data == "cancel-dictionary"

    override fun Bot.handle(chatId: ChatId.Id, callbackQuery: CallbackQuery) {
        val message = callbackQuery.message ?: return
        val messageText = message.text ?: return
        val userId = callbackQuery.from.id
        val ownerUserId = message.replyToMessage?.from?.id ?: return
        if (userId != ownerUserId) {
            answerCallbackQuery(
                callbackQueryId = callbackQuery.id,
                text = "Це не тобі вирішувати, зай 💅",
                showAlert = true,
            )
        } else {
            val dictionary = message.text?.retrieveDictionary()
            dictionary?.also {
                repository.delete(userId, it)

                val newText = messageText.removeDictionarySection()
                editMessageText(
                    chatId,
                    messageId = message.messageId,
                    text = newText,
                    parseMode = ParseMode.MARKDOWN,
                    replyMarkup = refreshResponseInlineKeyboard(dictionaryUpdated = false),
                )
            }
        }
    }

    private fun String.removeDictionarySection(): String =
        split(LanguageAssistant.NEW_WORDS_SECTION_LABEL)[0]
            .lines()
            .joinToString(separator = "\n") { it.boldifyIfLabel() }

    private fun String.boldifyIfLabel(): String =
        if (this in LanguageAssistant.LABELS) {
            "*$this*"
        } else {
            this
        }
}
