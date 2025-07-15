package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.reaction.ReactionType
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited
import io.github.rastiehaiev.service.UserInputAnalyzed

class DefaultUserInputHandler(
    private val repository: FileVocabularyRepository,
    private val textAnalyzationService: AiLanguageAssistantServiceRateLimited,
) : UserInputHandler {
    override fun isApplicable(message: Message, input: String) = true

    override fun Bot.handle(message: Message, input: String) {
        val chatId = message.chat.id
        textAnalyzationService.analyze(chatId, input)
            .onFailure { tooManyRequests(message) }
            .onSuccess { userInputAnalysed ->
                if (userInputAnalysed == null) {
                    reactWithHeart(message)
                } else {
                    val newWords = saveWordsMaybe(message, userInputAnalysed)
                    val resultText = generateResponseText(userInputAnalysed, newWords)
                    sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = resultText,
                        replyToMessageId = message.messageId,
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
            }
    }

    private fun saveWordsMaybe(message: Message, analyzed: UserInputAnalyzed) =
        if (analyzed.words != null) {
            message.targetChatId()?.let { repository.save(it, analyzed.words) }
        } else {
            null
        }

    private fun Message.targetChatId() =
        if (isPersonalChat()) {
            chat.id
        } else {
            from?.id
        }
}

private fun generateResponseText(
    userInputAnalyzed: UserInputAnalyzed,
    words: Map<String, String>?,
): String {
    fun createPart(label: String, text: String?) =
        if (text.isNullOrBlank()) {
            null
        } else {
            """
            |$label
            |${text.trim()}
            """.trimMargin()
        }

    val (corrected, explanation, alternative, translationUa, translationIt) = userInputAnalyzed
    val newWordsAsText = words?.map { (word, translation) -> "*$word* - $translation" }?.joinToString("\n")

    val parts = listOfNotNull(
        createPart("✅ *Правильний варіант*", corrected),
        createPart("ℹ️ *Пояснення*", explanation),
        createPart("🎯 *Альтернативний варіант*", alternative),
        createPart("🇺🇦 *Переклад українською*", translationUa),
        createPart("🇮🇹 *Переклад італійською*", translationIt),
        createPart("✍️ *Нові слова/фрази*", newWordsAsText),
    )
    return parts.joinToString(separator = "\n\n")
}

private fun Bot.reactWithHeart(message: Message) {
    setMessageReaction(
        chatId = ChatId.fromId(message.chat.id),
        messageId = message.messageId,
        reaction = listOf(ReactionType.Emoji("❤\uFE0F")),
    )
}
