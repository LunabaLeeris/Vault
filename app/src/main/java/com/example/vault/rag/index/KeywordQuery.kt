package com.example.vault.rag.index

import java.util.Locale

object KeywordQuery {
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from",
        "how", "i", "in", "is", "it", "me", "my", "of", "on", "or", "please", "the", "to",
        "was", "what", "when", "where", "which", "who", "with", "you", "your", "tell", "find",
        "show", "document", "documents", "file", "files",
    )

    fun terms(question: String, maxTerms: Int): List<String> = words.findAll(question.lowercase(Locale.ROOT))
        .map { it.value }.filter { it !in stopWords }.distinct().take(maxTerms).toList()

    // Only letter/number tokens reach MATCH. Raw user operators never enter SQL/FTS syntax.
    fun matchExpression(terms: List<String>): String = terms.joinToString(" OR ") { "\"$it\"" }

    fun score(text: String, title: String, terms: List<String>): Int {
        val body = words.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toSet()
        val heading = words.findAll(title.lowercase(Locale.ROOT)).map { it.value }.toSet()
        return terms.sumOf { (if (it in body) 10 else 0) + (if (it in heading) 3 else 0) }
    }
}
