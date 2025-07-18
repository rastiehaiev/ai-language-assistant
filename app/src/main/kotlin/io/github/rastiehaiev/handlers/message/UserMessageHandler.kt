package io.github.rastiehaiev.handlers.message

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import io.github.rastiehaiev.repository.VocabularyEntry

interface UserInputHandler {

    fun isApplicable(message: Message, input: String): Boolean

    fun Bot.handle(message: Message, input: String)
}

fun Message.isPersonalChat() = chat.type != "group"

fun Bot.tooManyRequests(message: Message) {
    sendMessage(
        chatId = ChatId.fromId(message.chat.id),
        text = "Забагато реквестів, лапуль \uD83D\uDE42\u200D↔\uFE0F На сьогодні всьо \uD83E\uDD72",
        replyToMessageId = message.messageId,
        parseMode = ParseMode.MARKDOWN,
    )
}

fun VocabularyEntry.toMarkup(): VocabularyEntryMarkup =
    VocabularyEntryMarkup(
        messageText = "*${key.escapeForMarkdownV2()}* \\- ||${value.escapeForMarkdownV2()}||",
        messageMarkup = InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = "⬅\uFE0F Назад",
                    callbackData = "review:back:${id}",
                ),
                InlineKeyboardButton.CallbackData(
                    text = "Видалити",
                    callbackData = "review:delete:${id}",
                ),
                InlineKeyboardButton.CallbackData(
                    text = "Далі ➡\uFE0F",
                    callbackData = "review:next:${id}",
                ),
            ),
        ),
    )

private fun String.escapeForMarkdownV2(): String {
    val text = this
    val specialChars = listOf('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!')
    return text.flatMap { if (it in specialChars) listOf('\\', it) else listOf(it) }.joinToString("")
}

data class VocabularyEntryMarkup(
    val messageText: String,
    val messageMarkup: InlineKeyboardMarkup,
)
