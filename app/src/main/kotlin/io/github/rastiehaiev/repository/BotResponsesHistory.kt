package io.github.rastiehaiev.repository

import io.github.rastiehaiev.service.UserInputAnalyzed
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class BotResponsesHistory {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val historyMap = ConcurrentHashMap<Pair<Long, Long>, Triple<Long, String, UserInputAnalyzed>>()

    fun store(
        chatId: Long,
        userId: Long,
        messageId: Long,
        inputText: String,
        userInputAnalyzed: UserInputAnalyzed,
    ) {
        logger.info("Storing bot response for chatId=$chatId, userId=$userId, messageId=$messageId.")
        historyMap[Pair(chatId, userId)] = Triple(messageId, inputText, userInputAnalyzed)
    }

    fun find(chatId: Long, userId: Long, messageId: Long): Pair<String, UserInputAnalyzed>? {
        return historyMap[chatId to userId]
            ?.takeIf { (msgId) -> msgId == messageId }
            ?.let { (_, inputText, userInputAnalyzed) -> inputText to userInputAnalyzed }
    }
}
