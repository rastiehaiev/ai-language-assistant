package io.github.rastiehaiev.handlers

import io.github.rastiehaiev.repository.BotResponsesHistory
import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited
import io.github.rastiehaiev.service.UserInputAnalyzed

class LanguageAssistant(
    private val repository: FileVocabularyRepository,
    private val botResponsesHistory: BotResponsesHistory,
    private val languageAssistantService: AiLanguageAssistantServiceRateLimited,
) {

    fun handle(chatId: Long, userId: Long, messageId: Long, input: String): Result<LanguageAssistanceResult> {
        val result = languageAssistantService.analyze(chatId, input)
        return if (result.isFailure) {
            Result.failure(result.exceptionOrNull()!!)
        } else {
            val userInputAnalysed = result.getOrNull()
            val languageAssistanceResult = if (userInputAnalysed == null) {
                LanguageAssistanceResult.Empty
            } else {
                botResponsesHistory.store(chatId, userId, messageId, input, userInputAnalysed)
                val newWords = saveWordsMaybe(userId, userInputAnalysed)
                val resultText = generateResponseText(userInputAnalysed, newWords)
                LanguageAssistanceResult.Success(resultText)
            }
            Result.success(languageAssistanceResult)
        }
    }

    private fun saveWordsMaybe(chatId: Long, analyzed: UserInputAnalyzed) =
        if (analyzed.words != null) {
            repository.save(chatId, analyzed.words)
        } else {
            null
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
}

sealed interface LanguageAssistanceResult {

    object Empty : LanguageAssistanceResult

    class Success(val responseText: String) : LanguageAssistanceResult
}
