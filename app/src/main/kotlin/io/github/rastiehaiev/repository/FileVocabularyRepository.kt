package io.github.rastiehaiev.repository

import java.io.File
import java.nio.file.Files
import java.util.*

class FileVocabularyRepository(private val directoryPath: String) {
    companion object {
        private const val SEPARATOR = " :: "
    }

    private val baseDirectory: File by lazy {
        File(directoryPath).also { it.mkdirs() }
    }

    fun findAll(chatId: Long): Map<String, List<String>> =
        getAllEntries(chatId)
            .groupBy({ it.key }, { it.value })
            .toSortedMap()

    fun findAllEntries(chatId: Long): List<VocabularyEntry> = getAllEntries(chatId)

    fun save(chatId: Long, words: Map<String, String>): Map<String, String> {
        fun wordLine(word: String, translation: String) = "$word$SEPARATOR$translation"

        val entries = getAllEntries(chatId).toSet()
        val entriesMap = entries.associateBy { (_, word, translation) -> wordLine(word, translation) }

        val newWords = words.filter { (word, translation) ->
            val wordString = wordLine(word, translation)
            !entriesMap.contains(wordString)
        }

        val newEntries = entries + newWords.map { VocabularyEntry(UUID.randomUUID(), it.key, it.value) }
        putInternal(chatId, newEntries)
        return newWords
    }

    private fun putInternal(chatId: Long, newEntries: Set<VocabularyEntry>) {
        val file = getOrCreateFile(chatId)

        val lines = newEntries
            .sortedBy { it.key }
            .map { (id, word, translation) -> "$id$SEPARATOR$word$SEPARATOR$translation" }
        Files.write(file.toPath(), lines)
    }

    private fun getAllEntries(chatId: Long): List<VocabularyEntry> =
        getOrCreateFile(chatId)
            .readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { it.split(SEPARATOR) }
            .filter { it.size == 3 }
            .map { VocabularyEntry(UUID.fromString(it[0].trim()), it[1].trim(), it[2].trim()) }

    private fun getOrCreateFile(chatId: Long) =
        File(baseDirectory, "${chatId}.txt")
            .also { it.createNewFile() }

    fun delete(chatId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(chatId).takeIf { it.isNotEmpty() }?.toMutableList() ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 } ?: return null

        allEntries.removeAt(index)
        putInternal(chatId, allEntries.toSet())

        return if (index < allEntries.size) {
            allEntries[index]
        } else {
            allEntries.first()
        }
    }

    fun next(chatId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(chatId).takeIf { it.isNotEmpty() } ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 }?.let { it + 1 } ?: return null

        return if (index < allEntries.size) {
            allEntries[index]
        } else {
            allEntries.first()
        }
    }

    fun back(chatId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(chatId).takeIf { it.isNotEmpty() } ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 } ?: return null
        val newIndex = index - 1

        return if (newIndex < 0) {
            allEntries.last()
        } else {
            allEntries[newIndex]
        }
    }
}

data class VocabularyEntry(
    val id: UUID,
    val key: String,
    val value: String,
)
