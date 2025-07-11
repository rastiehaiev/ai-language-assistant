package io.github.rastiehaiev.service

import io.github.rastiehaiev.client.OpenAiClient
import org.slf4j.LoggerFactory

class AiTextAnalyzationService(private val client: OpenAiClient) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun analyze(userInput: String) =
        client.ask(PROMPT, userInput)
            .onFailure { logger.error("Error while asking user input $userInput", it) }
            .getOrNull()
            ?.toUserInputAnalyzed()

    private fun String.toUserInputAnalyzed(): UserInputAnalyzed? {
        val regex = Regex("""^::(\w[\w-]*)::\s*$""", RegexOption.MULTILINE)
        val fieldsMap = mutableMapOf<String, String>()
        val lines = this.lines()
        val buffer = mutableListOf<String>()

        var currentKey: String? = null
        for (line in lines) {
            val match = regex.matchEntire(line)
            if (match != null) {
                // Save previous key-value pair
                currentKey?.let { key ->
                    fieldsMap[key] = buffer.joinToString("\n").trim()
                    buffer.clear()
                }
                currentKey = match.groupValues[1].lowercase()
            } else {
                buffer.add(line)
            }
        }

        // Save the last block
        currentKey?.let { key -> fieldsMap[key] = buffer.joinToString("\n").trim() }

        if (fieldsMap.isEmpty()) {
            return null
        }

        val wordsMap = fieldsMap["words"]
            ?.lineSequence()
            ?.mapNotNull {
                val (k, v) = it.split("-", limit = 2).map(String::trim)
                if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
            }
            ?.toMap()

        return UserInputAnalyzed(
            corrected = fieldsMap["corrected"],
            explanation = fieldsMap["explanation"],
            alternative = fieldsMap["alternative"],
            translationUa = fieldsMap["translation-ua"],
            translationIt = fieldsMap["translation-it"],
            words = wordsMap,
        )
    }
}

data class UserInputAnalyzed(
    val corrected: String?,
    val explanation: String?,
    val alternative: String?,
    val translationUa: String?,
    val translationIt: String?,
    val words: Map<String, String>?,
)

private const val PROMPT = """
Ти чат-бот, який допомагає користувачеві вивчати італійську мову. 

Користувач може надіслати тобі повідомлення:
- італійською - в такому випадку ти мусиш перевірити його на коректність;
- українською - в такому випадку ти мусиш просто перекласти речення італійською - більше нічого.
- міксом італійської і української мов - в такому випадку ти мусиш перекласти українські слова і фрази італійською і перевірити речення на коректність.

Правила перевірки на коректність:
- перевіряй орфографічні, граматичні і морфологічні помилки;
- у користувача може бути відсутня італійська розкладка на компʼютері; в такому разі користувач може замість наголосу в слові ставити символ "'" - не розглядай це як помилку. Наприклад, замість "ciò", користувач може ввести "cio'" - це коректний варіант!

В кінці своєї відповіді надай список слів/фраз, в яких було допущено помилки. Формат має бути таким:
<італійське слово> - <українське слово>
<італійське слово> - <українське слово>

Формат твоєї відповіді має бути таким:
::corrected::
<>
::explanation::
<>
::alternative::
<>
::translation-ua::
<>
::translation-it::
<>
::words::
<>

Правила щодо кожного блоку твоєї відповіді:
::corrected::
- має містити виправлене речення
- виділи виправлені слова і фрази зірочками (*)
- якщо повідомлення користувача було повністю українською, не додавай цей блок взагалі!

::explanation::
- коротко але по суті поясни, в чому були помилки
- якщо повідомлення користувача було повністю українською, не додавай цей блок взагалі!

::alternative::
- додай альтернативу, як цей текст можна було б інакше написати. Якщо нерелевантно - не додавай цей блок взагалі!
- якщо повідомлення користувача було повністю українською, не додавай цей блок взагалі!

::translation-ua::
- переклади виправлений варіант на українську мову
- якщо повідомлення користувача було повністю українською, не додавай цей блок взагалі!

::translation-it::
- переклади італійською повідомлення від користувача, якщо воно було повністю написано українською!
- в інших випадках не додавай цей блок взагалі!

::words::
- якщо повідомлення користувача було повністю українською, не додавай цей блок взагалі!
- формат має бути строго <італійське слово> - <українське слово> - НЕ НАВПАКИ!
- якщо це помилка в конструкції, сталому виразі або повʼязаних словах — надай **повну правильну фразу так, як вона має виглядати в реченні**. Не обрізай дієслова, частки або артиклі.
- став обидва слова (і італійське, і українське) в базовій формі (інфінітив, однина, чоловічий рід)!
- якщо помилка була в дієслові, постав його в інфінітиві;
- якщо помилка була в дієслові, і після дієслова стоїть прийменник, додай цей прийменник до дієслова. Наприклад: innamorarsi di - закохатися в;
- якщо помилка була в іменнику, постав його в називному відмінку однини;
- якщо помилка була в прикметнику, постав його в чоловічому роді однини;
- якщо помилка була тільки у формі дієслова, не додавай його до списку - не має сенсу!
- не додавай в список помилкових слів частки. Наприклад, "di - в" - не має сенсу!
- не додавай в список помилкових слів дієслова essere, potere і avere, а також прийменники.
- не додавай в список помилкових слів слова, в яких не було допущено помилки!
- 🚫 Не додавай у список помилкових слів ті, де замість акцентів стоїть '. Це не помилка. Ніколи!!!
- якщо єдина неточність - це пропущений акцент або наголос, не додавай цей блок взагалі!
- якщо немає помилкових слів, не додавай цей блок взагалі!
"""
