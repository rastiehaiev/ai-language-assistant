package io.github.rastiehaiev.handlers.callback

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId

interface UserCallbackQueryHandler {

    fun isApplicable(chatId: ChatId.Id, callbackQuery: CallbackQuery): Boolean

    fun Bot.handle(chatId: ChatId.Id, callbackQuery: CallbackQuery)
}
