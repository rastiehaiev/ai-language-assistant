package io.github.rastiehaiev.handlers.message

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.reaction.ReactionType
import io.github.rastiehaiev.handlers.LanguageAssistanceResult
import io.github.rastiehaiev.handlers.LanguageAssistant
import io.github.rastiehaiev.handlers.refreshResponseInlineKeyboard

class DefaultUserInputHandler(
    private val languageAssistant: LanguageAssistant,
) : UserInputHandler {
    override fun isApplicable(message: Message, input: String) = true

    override fun Bot.handle(message: Message, input: String) {
        val userId = message.from?.id ?: return
        val targetChatId = message.chat.id
        val messageId = message.messageId
        languageAssistant.handle(targetChatId, userId, messageId, input)
            .onFailure { tooManyRequests(message) }
            .onSuccess { result ->
                when (result) {
                    is LanguageAssistanceResult.Empty -> reactWithHeart(message)
                    is LanguageAssistanceResult.Success -> {
                        sendMessage(
                            chatId = ChatId.fromId(targetChatId),
                            text = result.responseText,
                            replyToMessageId = messageId,
                            parseMode = ParseMode.MARKDOWN,
                            replyMarkup = refreshResponseInlineKeyboard(messageId),
                        )
                    }
                }
            }
    }
}

private fun Bot.reactWithHeart(message: Message) {
    setMessageReaction(
        chatId = ChatId.fromId(message.chat.id),
        messageId = message.messageId,
        reaction = listOf(ReactionType.Emoji("❤\uFE0F")),
    )
}
