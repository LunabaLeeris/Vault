package com.example.vault.rag.index

import java.util.Locale

object KeywordQuery {
    private val words = Regex("[\\p{L}\\p{N}]+")
    val stopWords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "in", "on", "at", "to", "for", "with", "by", "from", "of", "about",
        "into", "through", "during", "before", "after", "above", "below", "up", "down", "is", "are", "was", "were",
        "be", "been", "being", "have", "has", "had", "do", "does", "did", "can", "could", "shall", "should", "will",
        "would", "may", "might", "must", "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your",
        "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its",
        "itself", "they", "them", "their", "theirs", "themselves", "what", "which", "who", "whom", "this", "that",
        "these", "those", "am", "how", "when", "where", "why", "list", "show", "find", "get", "tell", "give", "look",
        "check", "document", "documents", "file", "files", "all", "related", "realted", "regarding", "concerning",
        "search", "please", "any", "some", "every", "each", "that", "are", "regarding", "contains", "containing",
        "key", "terms", "term", "detail", "details", "info", "information", "summary", "summaries", "overview",
        "say", "says", "said", "state", "states", "mean", "means", "point", "points", "item", "items", "tell", "tells"
    )

    private val synonyms: Map<String, List<String>> = mapOf(
        "id" to listOf("id", "ids", "license", "licenses", "identification", "card", "passport", "permit"),
        "ids" to listOf("id", "ids", "license", "licenses", "identification", "card", "passport", "permit"),
        "license" to listOf("license", "licenses", "permit", "id", "driver", "drivers"),
        "licenses" to listOf("license", "licenses", "permit", "id", "driver", "drivers"),
        "driver" to listOf("driver", "drivers", "license", "licenses"),
        "drivers" to listOf("driver", "drivers", "license", "licenses"),
        "medical" to listOf("medical", "health", "hospital", "clinic", "lab", "prescription", "doctor", "patient", "record", "records"),
        "records" to listOf("record", "records", "medical", "health", "hospital", "clinic", "lab"),
        "record" to listOf("record", "records", "medical", "health", "hospital", "clinic", "lab"),
        "payment" to listOf("payment", "payments", "receipt", "gcash", "transaction", "billing", "bill"),
        "payments" to listOf("payment", "payments", "receipt", "gcash", "transaction", "billing", "bill"),
        "gcash" to listOf("gcash", "payment", "receipt", "wallet", "transfer"),
        "contract" to listOf("contract", "lease", "agreement", "terms"),
        "lease" to listOf("lease", "contract", "agreement", "residential", "rent")
    )

    fun tokens(value: String): Set<String> =
        words.findAll(value.lowercase(Locale.ROOT)).map { it.value }.toSet()

    fun terms(question: String, maxTerms: Int = 16): List<String> =
        words.findAll(question.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it.length > 1 && it !in stopWords }
            .distinct()
            .take(maxTerms)
            .toList()

    fun cleanQuery(question: String): String {
        val t = terms(question)
        return if (t.isNotEmpty()) t.joinToString(" ") else question.trim()
    }

    private fun stem(word: String): String = when {
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("es") && word.length > 4 -> word.dropLast(2)
        word.endsWith("s") && word.length >= 3 -> word.dropLast(1)
        else -> word
    }

    fun matchesTerm(term: String, tokenSet: Set<String>, fullText: String): Boolean {
        if (term in tokenSet || fullText.contains(term)) return true
        val termStem = stem(term)
        if (termStem in tokenSet || (termStem.length >= 2 && fullText.contains(termStem))) return true

        val tokenStems = tokenSet.map { stem(it) }
        if (termStem in tokenStems) return true

        if (tokenSet.any { token ->
            val tStem = stem(token)
            tStem == termStem || (token.length >= 4 && term.length >= 4 && (token.startsWith(termStem) || termStem.startsWith(tStem)))
        }) return true

        // Domain synonym matching
        val syns = synonyms[term] ?: synonyms[termStem]
        if (syns != null) {
            for (syn in syns) {
                if (syn in tokenSet || fullText.contains(syn)) return true
                val sStem = stem(syn)
                if (sStem in tokenSet || sStem in tokenStems) return true
            }
        }
        return false
    }

    fun lexicalScore(text: String, title: String, terms: List<String>): Float {
        if (terms.isEmpty()) return 0f
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerTitle = title.lowercase(Locale.ROOT)
        val textTokens = tokens(lowerText)
        val titleTokens = tokens(lowerTitle)

        var matchedCount = 0
        var titleMatchedCount = 0
        for (term in terms) {
            val inBody = matchesTerm(term, textTokens, lowerText)
            val inTitle = matchesTerm(term, titleTokens, lowerTitle)
            if (inBody) matchedCount++
            if (inTitle) titleMatchedCount++
        }

        if (matchedCount == 0 && titleMatchedCount == 0) return 0f

        val coverage = (matchedCount.coerceAtLeast(titleMatchedCount)).toFloat() / terms.size.toFloat()
        val titleBonus = if (titleMatchedCount > 0) 0.15f else 0.0f
        return (coverage * 0.85f + titleBonus).coerceIn(0f, 1f)
    }

    fun matchExpression(terms: List<String>) = terms.joinToString(" OR ") { "\"$it\"" }

    fun score(text: String, title: String, terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerTitle = title.lowercase(Locale.ROOT)
        val textTokens = tokens(lowerText)
        val titleTokens = tokens(lowerTitle)
        return terms.sumOf { term ->
            (if (matchesTerm(term, textTokens, lowerText)) 10 else 0) +
            (if (matchesTerm(term, titleTokens, lowerTitle)) 5 else 0)
        }
    }
}
