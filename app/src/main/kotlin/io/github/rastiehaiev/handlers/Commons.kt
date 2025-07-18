package io.github.rastiehaiev.handlers

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

fun refreshResponseInlineKeyboard(messageId: Long) =
    InlineKeyboardMarkup.create(
        listOf(
            InlineKeyboardButton.CallbackData(
                text = "Оновити",
                callbackData = "refresh:$messageId",
            ),
        )
    )
