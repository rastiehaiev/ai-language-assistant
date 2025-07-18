package io.github.rastiehaiev.handlers

import io.github.rastiehaiev.repository.FileVocabularyRepository
import io.github.rastiehaiev.service.AiLanguageAssistantServiceRateLimited
import io.github.rastiehaiev.service.UserInputAnalyzed

class LanguageAssistant(
    private val repository: FileVocabularyRepository,
    private val languageAssistantService: AiLanguageAssistantServiceRateLimited,
) {
    companion object {
        const val CORRECTION_SECTION_LABEL = "✅ Правильний варіант"
        const val EXPLANATION_SECTION_LABEL = "ℹ️ Пояснення"
        const val ALTERNATIVE_LABEL = "🎯 Альтернативний варіант"
        const val TRANSLATION_UA_LABEL = "🇺🇦 Переклад українською"
        const val TRANSLATION_IT_LABEL = "🇮🇹 Переклад італійською"
        const val NEW_WORDS_SECTION_LABEL = "✍️ Нові слова/фрази"

        val LABELS = setOf(
            CORRECTION_SECTION_LABEL,
            EXPLANATION_SECTION_LABEL,
            ALTERNATIVE_LABEL,
            TRANSLATION_UA_LABEL,
            TRANSLATION_IT_LABEL,
            NEW_WORDS_SECTION_LABEL,
        )
    }

    fun handle(chatId: Long, userId: Long, input: String): Result<LanguageAssistanceResult> {
        val result = languageAssistantService.analyze(chatId, input)
        return if (result.isFailure) {
            Result.failure(result.exceptionOrNull()!!)
        } else {
            val userInputAnalysed = result.getOrNull()
            val languageAssistanceResult = if (userInputAnalysed == null) {
                LanguageAssistanceResult.Empty
            } else {
                val newWords = saveWordsMaybe(userId, userInputAnalysed)
                val resultText = generateResponseText(userInputAnalysed, newWords)
                LanguageAssistanceResult.Success(resultText, dictionaryItemsCount = newWords?.size ?: 0)
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
                |*$label*
                |${text.trim()}
                """.trimMargin()
            }

        val (corrected, explanation, alternative, translationUa, translationIt) = userInputAnalyzed
        val newWordsAsText = words?.map { (word, translation) -> "*$word* - $translation" }?.joinToString("\n")

        val parts = listOfNotNull(
            createPart(CORRECTION_SECTION_LABEL, corrected),
            createPart(EXPLANATION_SECTION_LABEL, explanation),
            createPart(ALTERNATIVE_LABEL, alternative),
            createPart(TRANSLATION_UA_LABEL, translationUa),
            createPart(TRANSLATION_IT_LABEL, translationIt),
            createPart(NEW_WORDS_SECTION_LABEL, newWordsAsText),
        )
        return parts.joinToString(separator = "\n\n")
    }
}

sealed interface LanguageAssistanceResult {

    object Empty : LanguageAssistanceResult

    class Success(val responseText: String, val dictionaryItemsCount: Int) : LanguageAssistanceResult
}
