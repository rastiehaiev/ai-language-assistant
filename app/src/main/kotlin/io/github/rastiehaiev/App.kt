package io.github.rastiehaiev

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.reaction.ReactionType
import com.github.kotlintelegrambot.logging.LogLevel
import io.github.rastiehaiev.client.OpenAiClient
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.repository.VocabularyEntry
import io.github.rastiehaiev.service.OpenAiService
import java.util.*

private fun getEnv(name: String) = System.getenv(name) ?: error("Environment variable '$name' not set!")

fun main() {
    val persistenceDir = getEnv("AI_LANG_ASSISTANT_PERSISTENCE_DIR")
    val openaiApiKey = getEnv("AI_LANG_ASSISTANT_OPENAI_API_KEY")
    val telegramBotToken = getEnv("AI_LANG_ASSISTANT_TELEGRAM_BOT_TOKEN")

    val repository = FileVocabularyRepository(directoryPath = persistenceDir)
    val openAiClient = OpenAiClient(apiKey = openaiApiKey)
    val openAiService = RateLimitedOpenAiService(OpenAiService(openAiClient))
    val bot = bot {
        token = telegramBotToken
        logLevel = LogLevel.All(networkLogLevel = LogLevel.Network.Body)

        dispatch {
            callbackQuery {
                val chatId = callbackQuery.message?.chat?.id?.let { ChatId.fromId(it) } ?: return@callbackQuery
                val callbackData = callbackQuery.data

                fun String.toEntryId(label: String): UUID = this.removePrefix(label).let { UUID.fromString(it) }

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
                        bot.deleteMessage(chatId, messageId)
                    }
                } else {
                    val messageId = callbackQuery.message?.messageId
                    if (messageId != null) {
                        with(nextEntry.toMarkup()) {
                            bot.editMessageText(
                                chatId,
                                messageId,
                                inlineMessageId = null,
                                text = messageText,
                                parseMode = ParseMode.MARKDOWN_V2,
                                replyMarkup = messageMarkup,
                            )
                        }
                    }
                }
            }
            message {
                val text = message.text?.trim() ?: return@message
                val chatId = message.chat.id
                val chatType = message.chat.type

                if (chatType == "group") {
                    openAiService.fixUserInputInGroup(chatId, text)
                        .onFailure {
                            bot.tooManyRequests(message)
                        }.onSuccess { responseText ->
                            if (responseText != null) {
                                bot.sendMessage(
                                    chatId = ChatId.fromId(message.chat.id),
                                    text = responseText,
                                    replyToMessageId = message.messageId,
                                    parseMode = ParseMode.MARKDOWN,
                                )
                            } else {
                                bot.reactWithHeart(message)
                            }
                        }
                } else {
                    if (text == "/push") {
                        bot.sendMessage(
                            chatId = ChatId.fromId(message.chat.id),
                            text = "Я поки що цього не вмію \uD83D\uDE14",
                            replyToMessageId = message.messageId,
                        )
                    } else if (text == "/review") {
                        val entries = repository.findAllEntries(chatId)
                        if (entries.isEmpty()) {
                            bot.sendMessage(
                                chatId = ChatId.fromId(message.chat.id),
                                text = "У вас ще немає збережених слів.",
                                replyToMessageId = message.messageId,
                            )
                        } else {
                            val firstEntry = entries.first()
                            with(firstEntry.toMarkup()) {
                                bot.sendMessage(
                                    chatId = ChatId.fromId(chatId),
                                    parseMode = ParseMode.MARKDOWN_V2,
                                    text = messageText,
                                    replyMarkup = messageMarkup,
                                )
                            }
                        }
                    } else if (text == "/list") {
                        val words = repository.findAll(chatId)
                        val text = if (words.isEmpty()) {
                            "У вас ще немає збережених слів."
                        } else {
                            words.map { "*${it.key}* - ${it.value.joinToString(separator = ", ")}" }
                                .joinToString(separator = "\n")
                        }
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = text,
                            parseMode = ParseMode.MARKDOWN,
                        )
                    } else {
                        val userWords = text.toWordsMap()
                        if (userWords == null) {
                            openAiService.fixUserInputInPersonalChat(chatId, text)
                                .onFailure {
                                    bot.tooManyRequests(message)
                                }.onSuccess { userInputAnalysed ->
                                    if (userInputAnalysed == null) {
                                        bot.reactWithHeart(message)
                                    } else {
                                        val (corrected, explanation, alternative, words) = userInputAnalysed
                                        val newWords = if (words != null) {
                                            repository.save(chatId, words)
                                        } else {
                                            null
                                        }

                                        val resultText =
                                            generateResponseText(corrected, explanation, alternative, newWords)
                                        bot.sendMessage(
                                            chatId = ChatId.fromId(message.chat.id),
                                            text = resultText,
                                            replyToMessageId = message.messageId,
                                            parseMode = ParseMode.MARKDOWN,
                                        )
                                    }
                                }
                        } else {
                            repository.save(chatId, userWords)
                            bot.reactWithNoted(message)
                        }
                    }
                }
            }
        }
    }

    bot.startPolling()
}

private fun String.toWordsMap(): Map<String, String>? =
    lines()
        .mapNotNull { line ->
            val parts = line.split("::").map { it.trim() }
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                null
            }
        }
        .toMap()
        .takeIf { it.isNotEmpty() }

private fun generateResponseText(
    corrected: String,
    explanation: String,
    alternative: String?,
    words: Map<String, String>?,
): String {
    val alternativePart = if (alternative != null) {
        """
        |
        |🎯 *Альтернативний варіант*
        |$alternative
        |
    """.trimMargin()
    } else {
        ""
    }
    val newWordsPart = if (!words.isNullOrEmpty()) {
        """
        |
        |✍️ *Нові слова/фрази*
        |${words.map { (word, translation) -> "*$word* - $translation" }.joinToString("\n")}
    """.trimMargin()
    } else {
        ""
    }

    return """
        |✅ *Правильний варіант*
        |$corrected
        |
        |ℹ️ *Пояснення*
        |$explanation
        |$alternativePart$newWordsPart
    """.trimMargin()
}

private fun Bot.reactWithHeart(message: Message) {
    setMessageReaction(
        chatId = ChatId.fromId(message.chat.id),
        messageId = message.messageId,
        reaction = listOf(ReactionType.Emoji("❤\uFE0F")),
    )
}

private fun Bot.reactWithNoted(message: Message) {
    setMessageReaction(
        chatId = ChatId.fromId(message.chat.id),
        messageId = message.messageId,
        reaction = listOf(ReactionType.Emoji("✍\uFE0F")),
    )
}

private fun Bot.tooManyRequests(message: Message) {
    sendMessage(
        chatId = ChatId.fromId(message.chat.id),
        text = "Забагато реквестів, лапуль \uD83D\uDE42\u200D↔\uFE0F На сьогодні всьо \uD83E\uDD72",
        replyToMessageId = message.messageId,
        parseMode = ParseMode.MARKDOWN,
    )
}

private fun VocabularyEntry.toMarkup(): VocabularyEntryMarkup =
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

fun String.escapeForMarkdownV2(): String {
    val text = this
    val specialChars = listOf('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!')
    return text.flatMap { if (it in specialChars) listOf('\\', it) else listOf(it) }.joinToString("")
}

private data class VocabularyEntryMarkup(
    val messageText: String,
    val messageMarkup: InlineKeyboardMarkup,
)
