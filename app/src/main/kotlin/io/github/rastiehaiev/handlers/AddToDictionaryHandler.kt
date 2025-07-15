package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.reaction.ReactionType
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited

class AddToDictionaryHandler(
    private val repository: FileVocabularyRepository,
    private val languageAssistantService: AiLanguageAssistantServiceRateLimited,
) : UserInputHandler {
    override fun isApplicable(message: Message, input: String) =
        input.lines().all { it.contains("::") }

    override fun Bot.handle(message: Message, input: String) {
        val chatId = message.chat.id

        val (translatedEntries, keysToBeTranslated) = input.parse()
        if (keysToBeTranslated.isEmpty()) {
            repository.save(chatId, translatedEntries)
            reactWithNoted(message)
        } else {
            languageAssistantService.translate(chatId, keysToBeTranslated)
                .onFailure { tooManyRequests(message) }
                .onSuccess { justTranslatedEntries ->
                    val allEntries = LinkedHashMap<String, String>()
                    allEntries.putAll(translatedEntries)
                    allEntries.putAll(justTranslatedEntries)

                    val actuallySavedWords = repository.save(chatId, allEntries)
                    val resultText = if (actuallySavedWords.isNotEmpty()) {
                        generateResponseText(actuallySavedWords)
                    } else {
                        "Нових слів не знайдено."
                    }
                    sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = resultText,
                        replyToMessageId = message.messageId,
                        parseMode = ParseMode.MARKDOWN,
                    )
                }
        }
    }
}

private fun String.parse(): Pair<Map<String, String>, Set<String>> {
    val translatedEntries = HashMap<String, String>()
    val keysToBeTranslated = HashSet<String>()

    lines()
        .forEach { line ->
            val parts = line.split("::").map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size == 2) {
                translatedEntries[parts[0]] = parts[1]
            } else if (parts.size == 1) {
                keysToBeTranslated.add(parts[0])
            }
        }
    return translatedEntries to keysToBeTranslated
}

private fun generateResponseText(words: Map<String, String>): String {
    val wordsPart = words.map { (key, value) -> "*$key* - $value" }.joinToString("\n")
    return "✍️ Записую:\n$wordsPart".trim()
}

private fun Bot.reactWithNoted(message: Message) {
    setMessageReaction(
        chatId = ChatId.fromId(message.chat.id),
        messageId = message.messageId,
        reaction = listOf(ReactionType.Emoji("✍\uFE0F")),
    )
}
