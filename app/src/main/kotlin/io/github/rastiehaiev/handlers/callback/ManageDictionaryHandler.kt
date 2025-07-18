package io.github.rastiehaiev.handlers.callback

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.handlers.message.toMarkup
import io.github.rastiehaiev.repository.FileVocabularyRepository
import java.util.*

class ManageDictionaryHandler(
    private val repository: FileVocabularyRepository,
) : UserCallbackQueryHandler {
    override fun isApplicable(chatId: ChatId.Id, callbackQuery: CallbackQuery): Boolean =
        callbackQuery.data.startsWith("review:")

    override fun Bot.handle(chatId: ChatId.Id, callbackQuery: CallbackQuery) {
        fun String.toEntryId(label: String): UUID = this.removePrefix(label).let { UUID.fromString(it) }

        val callbackData = callbackQuery.data
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
                deleteMessage(chatId, messageId)
            }
        } else {
            val messageId = callbackQuery.message?.messageId
            if (messageId != null) {
                nextEntry.toMarkup().apply {
                    editMessageText(
                        chatId,
                        messageId,
                        text = messageText,
                        parseMode = ParseMode.MARKDOWN_V2,
                        replyMarkup = messageMarkup,
                    )
                }
            }
        }
    }
}
