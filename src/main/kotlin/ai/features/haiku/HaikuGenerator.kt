package org.example.ai.features.haiku

import org.example.ai.AIModel
import org.example.ai.createAIClient

class HaikuGenerator(val model: AIModel) {
    val prompt = """
    You are a creative and talented poet proficient in Japanese versification.
    
    You goal is to create a haiku about the given word. Haiku should follow the typical haiku structure in English adaptation.
    
    The most important aspects are:
    * Must consists of three lines
    * Must be composed of 17 morae (called "on" in Japanese)   
    * Each morae must be represented by a single English syllable
    * The number of morae must be: 5 in the first line, 7 in the second line, and 5 in the third line.
    * The haiku should include a kireji, or "cutting word", and a kigo, or seasonal reference.    
    * The haiku must contain the word provided.
    
    Adhere to all the rules listed above.
    Generate a haiku about "%s".
    """.trimIndent()

    val temperature = 1.0

    /**
    Generate a haiku using the [word] provided.
     */
    suspend fun generateHaiku(word: String): String {
        val client = createAIClient()
        return client.chatRequest(model, String.format(prompt, word), temperature = temperature)
    }
}
