package io.github.rastiehaiev.handlers.message

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.repository.FileVocabularyRepository

class ReviewDictionaryHandler(
    private val repository: FileVocabularyRepository,
): UserInputHandler {

    override fun isApplicable(message: Message, input: String) =
        input == "/review" && message.isPersonalChat()

    override fun Bot.handle(message: Message, input: String) {
        val userId = message.from?.id ?: return
        val entries = repository.findAllEntries(userId)
        if (entries.isEmpty()) {
            sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "У вас ще немає збережених слів.",
                replyToMessageId = message.messageId,
            )
        } else {
            val firstEntry = entries.first()
            with(firstEntry.toMarkup()) {
                sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    parseMode = ParseMode.MARKDOWN_V2,
                    text = messageText,
                    replyMarkup = messageMarkup,
                )
            }
        }
    }
}
