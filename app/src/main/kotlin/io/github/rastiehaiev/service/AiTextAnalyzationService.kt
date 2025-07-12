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
