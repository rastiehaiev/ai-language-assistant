package io.github.rastiehaiev.service

import io.github.rastiehaiev.client.OpenAiClient
import org.slf4j.LoggerFactory

private const val LABEL_CORRECTED = "✅ Правильний варіант"
private const val LABEL_EXPLANATION = "ℹ️ Пояснення"
private const val LABEL_ALTERNATIVE = "🔁 Альтернативний варіант"
private const val LABEL_NEW_WORDS = "✍️ Нові слова/фрази"
private const val LABEL_TRANSLATION_UA = "🇺🇦 Переклад українською"

class OpenAiService(private val client: OpenAiClient) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun fixUserInputInGroup(userInput: String) =
        ask(systemAdvice = GROUP_CHAT_PROMPT, userInput).getOrNull()

    fun fixUserInputInPersonalChat(userInput: String) =
        ask(systemAdvice = PERSONAL_CHAT_PROMPT, userInput)
            .map { it?.toUserInputAnalysed() }
            .getOrNull()

    private fun ask(systemAdvice: String, userInput: String) =
        client.ask(systemAdvice, userInput)
            .onFailure { logger.error("Error while asking user input $userInput", it) }

    private fun String.toUserInputAnalysed(): UserInputAnalysed? {
        fun extractBlock(label: String): String? {
            val regex = Regex("(?s)$label:\\n+(.*?)(?=\\n+[️ℹ️✅🔁✍️]|$)")
            return regex.find(this)?.groupValues?.get(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { it.length >= 5 }
        }

        val wordsString = extractBlock(LABEL_NEW_WORDS)
        val corrected = extractBlock(LABEL_CORRECTED) ?: return null
        val explanation = extractBlock(LABEL_EXPLANATION) ?: return null
        val words = wordsString?.parseWords()
        return UserInputAnalysed(
            corrected = corrected,
            explanation = explanation,
            alternative = extractBlock(LABEL_ALTERNATIVE),
            words = words,
        )
    }

    private fun String.parseWords(): Map<String, String>? {
        val words = trim()
            .split("\n")
            .mapNotNull { line: String ->
                line.split("-")
                    .takeIf { it.size == 2 }
                    ?.let { keyAndValue ->
                        val key = keyAndValue[0].trim()
                        val value = keyAndValue[1].trim()
                        if (key.isNotBlank() && value.isNotBlank()) {
                            key to value
                        } else {
                            null
                        }
                    }
            }
            .associate { it }
            .takeIf { it.isNotEmpty() }
        return words
    }
}

data class UserInputAnalysed(
    val corrected: String,
    val explanation: String,
    val alternative: String?,
    val words: Map<String, String>?,
)

private const val GROUP_CHAT_PROMPT = """
Ти мовний асистент у Telegram-групі, який допомагає людям вивчати італійську мову. Користувачі часто пишуть речення італійською або міксом італійської з українською.

Коли хтось надсилає повідомлення, ти повинен:

1. **Виправити помилки** в реченні (граматика, лексика, узгодження). Не виправляй випадкові друкарські помилки чи відсутність наголосів.
2. **Пояснити**, чому були зроблені ці виправлення (коротко і зрозуміло).
3. **Перекласти речення на українську мову.**
4. **(Опціонально)**: якщо існує природна **альтернатива**, запропонуй її з поясненням (але не вигадуй штучних варіантів).

🔹 Якщо повідомлення не містить італійської — не відповідай.
🔹 Якщо повідомлення повністю правильне — просто відповідай: "-".
🔹 Якщо в реченні трапляються українські слова — переклади їх на італійську в контексті речення. Не залишай українські слова у виправленій італійській версії.
🔹 Відповідай стисло, доброзичливо і точно.

Повертай відповідь у такому форматі:

$LABEL_CORRECTED:  
[виправлене італійське речення]

$LABEL_EXPLANATION:  
[чому так, які правила порушено]

$LABEL_TRANSLATION_UA:  
[переклад]

$LABEL_ALTERNATIVE:  
[альтернатива] — [пояснення]
"""

private const val PERSONAL_CHAT_PROMPT = """
Ти чат-бот, який допомагає користувачеві вивчати італійську мову. Спілкування відбувається один на один.

На кожне повідомлення користувача ти мусиш:

1. Аналізувати речення на граматичні помилки, неправильне вживання слів або синтаксис.
2. Якщо повідомлення повністю правильне — просто відповідай: "-".
3. Якщо є помилки, відповідай у такій структурі:

$LABEL_CORRECTED:
<наступний рядок – правильно сформульоване речення>

$LABEL_EXPLANATION:
<коротке пояснення, чому було неправильно і як правильно>

$LABEL_ALTERNATIVE:
<альтернативний природний або розмовний варіант, якщо такий є>

$LABEL_NEW_WORDS:
Перелічуй лише ті слова або фрази, які:
– були вжиті неправильно,
– або були вставлені українською.

Для кожної з них:
– Якщо це окреме слово (іменник, дієслово, прикметник) — переклади його в базовій формі: інфінітив / називний / чоловічий рід. Для іменників вкажи також артикль (un або una).
- Україньке слово також постав у базовій формі: інфінітив / називний / чоловічий рід.
– Якщо це помилка в конструкції, сталому виразі або повʼязаних словах — надай **повну правильну фразу так, як вона має виглядати в реченні**. Не обрізай дієслова, частки або артиклі.

Формат:
<італійське слово або повна фраза> - <український переклад>

Правила:
– Якщо користувач надсилає речення українською, а потім переклад італійською — порівняй переклад з оригіналом, знайди помилки, виправи, поясни.
– Якщо повідомлення — це мікс української та італійської, переклади все італійською та виправи, якщо потрібно.
– Якщо в повідомленні **немає італійської мови** — відповідай просто: "-".
– Не виправляй друкарські помилки, літери без наголосу або незначні технічні похибки.
- Нові слова: зліва італійське слово, справа українське!

Твоя мета — бути доброзичливим, коректним і чітким помічником із граматики. Відповідай стисло й по суті.
"""
