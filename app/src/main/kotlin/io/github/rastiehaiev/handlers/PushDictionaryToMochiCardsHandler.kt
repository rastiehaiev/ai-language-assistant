package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import io.github.rastiehaiev.service.MochiService

class PushDictionaryToMochiCardsHandler(
    private val mochiService: MochiService,
) : UserInputHandler {
    override fun isApplicable(message: Message, input: String) =
        input == "/push" && message.isPersonalChat()

    override fun Bot.handle(message: Message, input: String) {
        val chatId = message.chat.id
        sendMessage(
            chatId = ChatId.fromId(message.chat.id),
            text = mochiService.uploadFor(chatId),
            replyToMessageId = message.messageId,
        )
    }
}
