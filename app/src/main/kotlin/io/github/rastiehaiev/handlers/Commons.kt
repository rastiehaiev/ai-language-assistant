package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

fun refreshResponseInlineKeyboard(dictionaryUpdated: Boolean): InlineKeyboardMarkup {
    val refreshButton = InlineKeyboardButton.CallbackData(
        text = "Оновити",
        callbackData = "refresh",
    )
    val cancelDictionaryButton = if (dictionaryUpdated) {
        InlineKeyboardButton.CallbackData(
            text = "Не додавай слова",
            callbackData = "cancel-dictionary",
        )
    } else {
        null
    }
    return InlineKeyboardMarkup.create(
        listOfNotNull(
            refreshButton,
            cancelDictionaryButton,
        )
    )
}



fun String.retrieveDictionary(): Map<String, String>? {
    return split(LanguageAssistant.NEW_WORDS_SECTION_LABEL)
        .takeIf { it.size == 2 }
        ?.get(1)
        ?.lines()
        ?.map { line -> line.split("-").map { it.trim() } }
        ?.filter { it.size == 2 }
        ?.map { it[0].removeSurrounding("*") to it[1] }
        ?.associate { it }
}
