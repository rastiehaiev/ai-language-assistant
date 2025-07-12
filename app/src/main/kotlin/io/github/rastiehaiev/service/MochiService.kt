package io.github.rastiehaiev.service

import io.github.rastiehaiev.client.MochiClient
import io.github.rastiehaiev.repository.FileVocabularyRepository
import org.slf4j.LoggerFactory

class MochiService(
    private val mochiClient: MochiClient,
    private val repository: FileVocabularyRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val defaultDeckId = "rYmop38O"

    fun uploadFor(chatId: Long): String =
        if (chatId != 228089372L) {
            "Я поки що цього не вмію."
        } else {
            val wordsMap = repository.findAll(chatId)
            if (wordsMap.isEmpty()) {
                "У вас ще немає слів, щоб загрузити в Mochi Cards."
            } else {
                try {
                    val saved = mochiClient.save(defaultDeckId, wordsMap)
                    if (saved > 0) {
                        "Загружено $saved слів в Mochi Cards."
                    } else {
                        "Не вдалося загрузити слова в Mochi Cards."
                    }
                } catch (e: Exception) {
                    logger.error("Failed to save words: ${e.message}", e)
                    "Unknown error"
                }
            }
        }
}
