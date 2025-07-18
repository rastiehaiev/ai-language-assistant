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

    fun findAll(userId: Long): Map<String, List<String>> =
        findAllEntries(userId)
            .groupBy({ it.key }, { it.value })
            .toSortedMap()

    fun findAllEntries(userId: Long): List<VocabularyEntry> =
        getOrCreateFile(userId)
            .readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { it.split(SEPARATOR) }
            .filter { it.size == 3 }
            .map { VocabularyEntry(UUID.fromString(it[0].trim()), it[1].trim(), it[2].trim()) }

    fun save(userId: Long, words: Map<String, String>): Map<String, String> {
        fun wordLine(word: String, translation: String) = "$word$SEPARATOR$translation"

        val entries = findAllEntries(userId).toSet()
        val entriesMap = entries.associateBy { (_, word, translation) -> wordLine(word, translation) }

        val newWords = words.filter { (word, translation) ->
            val wordString = wordLine(word, translation)
            !entriesMap.contains(wordString)
        }

        val newEntries = entries + newWords.map { VocabularyEntry(UUID.randomUUID(), it.key, it.value) }
        putInternal(userId, newEntries)
        return newWords
    }

    fun delete(userId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(userId).takeIf { it.isNotEmpty() }?.toMutableList() ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 } ?: return null

        allEntries.removeAt(index)
        putInternal(userId, allEntries.toSet())

        return if (index < allEntries.size) {
            allEntries[index]
        } else {
            allEntries.first()
        }
    }

    fun delete(userId: Long, words: Map<String, String>) {
        val entries = findAllEntries(userId).toSet()
        val wordPairs = words.map { (word, translation) -> word to translation }
        val entriesMap = entries.associateBy { (_, word, translation) -> word to translation }.toMutableMap()

        wordPairs.forEach { pair -> entriesMap.remove(pair) }

        val resultEntries = entriesMap.values.toSet()
        putInternal(userId, resultEntries)
    }

    fun deleteAll(userId: Long) {
        putInternal(userId, emptySet())
    }

    fun next(userId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(userId).takeIf { it.isNotEmpty() } ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 }?.let { it + 1 } ?: return null

        return if (index < allEntries.size) {
            allEntries[index]
        } else {
            allEntries.first()
        }
    }

    fun back(userId: Long, entryId: UUID): VocabularyEntry? {
        val allEntries = findAllEntries(userId).takeIf { it.isNotEmpty() } ?: return null
        val index = allEntries.indexOfFirst { it.id == entryId }.takeIf { it >= 0 } ?: return null
        val newIndex = index - 1

        return if (newIndex < 0) {
            allEntries.last()
        } else {
            allEntries[newIndex]
        }
    }

    private fun putInternal(userId: Long, newEntries: Set<VocabularyEntry>) {
        val file = getOrCreateFile(userId)

        val lines = newEntries
            .sortedBy { it.key }
            .map { (id, word, translation) ->
                val wordNormalized = word.removeSurrounding("*").lowercase()
                val translationNormalized = translation.removeSurrounding("*").lowercase()
                "$id$SEPARATOR$wordNormalized$SEPARATOR$translationNormalized"
            }
        Files.write(file.toPath(), lines)
    }

    private fun getOrCreateFile(userId: Long) =
        File(baseDirectory, "${userId}.txt").also { it.createNewFile() }
}

data class VocabularyEntry(
    val id: UUID,
    val key: String,
    val value: String,
)
