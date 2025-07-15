package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import io.github.rastiehaiev.repository.FileVocabularyRepository

class ListDictionaryEntriesHandler(
    private val repository: FileVocabularyRepository,
) : UserInputHandler {
    override fun isApplicable(message: Message, input: String) =
        input == "/list" && message.isPersonalChat()

    override fun Bot.handle(message: Message, input: String) {
        val chatId = message.chat.id
        val words = repository.findAll(chatId)
        val text = if (words.isEmpty()) {
            "У вас ще немає збережених слів."
        } else {
            words.map { "*${it.key}* - ${it.value.joinToString(separator = ", ")}" }
                .joinToString(separator = "\n")
        }
        sendMessage(
            chatId = ChatId.fromId(chatId),
            text = text,
            parseMode = ParseMode.MARKDOWN,
        )
    }
}
