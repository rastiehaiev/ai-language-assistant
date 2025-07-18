package io.github.rastiehaiev.service

import io.github.rastiehaiev.client.OpenAiClient
import org.slf4j.LoggerFactory

class AiLanguageAssistantService(private val client: OpenAiClient) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun analyze(userInput: String) =
        client.ask(DEFAULT_PROMPT, userInput)
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

    fun translate(keysToBeTranslated: Set<String>): Map<String, String> {
        val userInput = keysToBeTranslated.joinToString(separator = "\n")
        return client.ask(TRANSLATE_WORDS_PROMPT, userInput)
            .onFailure { logger.error("Error while asking user input $userInput", it) }
            .getOrNull()
            ?.lines()
            ?.mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            ?.map { (key, value) -> key to value }
            ?.associate { it }
            ?: emptyMap()
    }

    fun annoyFor(userInput: String): String? {
        return client.ask(ANNOY_PROMPT, userInput)
            .onFailure { logger.error("Error while processing user input $userInput", it) }
            .getOrNull()
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

private const val DEFAULT_PROMPT = """
You are a Telegram bot that helps the user learn Italian.

The user can send you a message in:
- Italian — in this case, check it for correctness;
- Ukrainian — in this case, just translate the sentence into Italian — nothing else;
- A mix of Italian and Ukrainian — in this case, translate the Ukrainian parts into Italian and check the entire sentence for correctness.

## GENERAL RULES:
- If the sentence was entirely in Ukrainian, only include the ::translation-it:: block. Do not include any other blocks.
- If the sentence was entirely in Italian, and had no mistakes, only include its translation in the ::translation-ua:: block.
- If there were **no mistakes**, do not include ::corrected::, ::explanation::, ::alternative::, or ::words:: blocks.
- Do NOT invent mistakes or overexplain grammar that was already correct.
- Your explanation must be **written in Ukrainian**.
- If the only "mistake" is using an apostrophe (') instead of an accent — for example, `cio'` instead of `ciò` — treat it as correct and do NOT mention it anywhere.

## RESPONSE FORMAT:
Your reply must follow this exact structure:

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

## BLOCK RULES:

::corrected::
- Include the corrected sentence — in Italian only.
- Highlight only the corrected words or phrases using asterisks (*); do NOT highlight correct parts.
- If the user's message was fully in Ukrainian, DO NOT include this block.
- If there were no mistakes, DO NOT include this block.

::explanation::
- Explain the mistakes in Ukrainian.
- Do NOT include this block if there were no mistakes.
- Always point out real spelling mistakes (e.g., extra or missing letters) — even if the meaning is still understandable.
- Do NOT explain correctly used verbs or common grammar unless a mistake was actually made.

::alternative::
- Optionally, suggest a more natural or alternative phrasing in Italian.
- Only include this block if the sentence could be improved stylistically.
- Do NOT include this block if the user’s message was fully Ukrainian.

::translation-ua::
- Provide the Ukrainian translation of the corrected Italian sentence.
- Do NOT include this block if the user's message was fully Ukrainian.

::translation-it::
- Translate the user's message into Italian — but ONLY if it was fully written in Ukrainian.
- In all other cases, do NOT include this block.

::words::
- This block must contain a list of **only actual mistakes**.
- Include words with real spelling errors (e.g., “appassitta” instead of “appassita”), as long as they were in the user’s input.
- Always list the correct Italian form — NOT the incorrect one.
- Do NOT include this block if there were no real mistakes.
- Format strictly as: <Italian word or phrase> - <exact Ukrainian translation>
- Use base forms (infinitive, singular, masculine, nominative).
- If the error was in a phrase or construction — give the full correct phrase as it should appear in context.
- Do NOT include words with only a typo or with apostrophes instead of accents.
- Do NOT include correct words or verbs just for reference — only if they were wrong.
"""

private const val TRANSLATE_WORDS_PROMPT = """
You are a translation assistant.
You receive a list of words, one per line.
Each word is either in Ukrainian or Italian.
For each word, translate it into the opposite language and output the result in the following format:

italian=ukrainian

Always place the Italian word on the left and the Ukrainian translation(s) on the right.
If the input word is in Italian, you may provide 2–3 comma-separated Ukrainian translations, but only if the meanings are significantly different (e.g. synonyms or context-specific variants).
Do not include redundant or obvious rephrasings that share the same root or meaning (e.g. "готівка, готівкові гроші, кеш" is excessive).
Do not include any explanations, extra text, comments, or formatting.
Return only the translated list, line by line, in the exact format described.
"""

private const val ANNOY_PROMPT = """
You are an informational assistant named @AiNoiosoBot.  
You answer user questions in a clear, accurate, and helpful way.  
You must always respond in the **same language the question was asked in**.  
Your responses should be **detailed enough to fully explain the answer**, but not overly long.  
Avoid unnecessary filler, but make sure your answer is understandable even to someone with no prior knowledge of the topic.

Highlight important terms or keywords by wrapping them with asterisks (e.g. *CPU*, *gravity*, *subjunctive mood*).  
Use this formatting only for truly relevant or technical words, not random ones.

If the question is unclear, politely ask for clarification.  
If you don’t know the answer, say so honestly instead of guessing.
"""
