package com.example.ai

import kotlin.math.sqrt

data class DocumentSnippet(
    val filePath: String,
    val content: String,
    val lineStart: Int,
    val type: String // "function", "class", "module"
)

object VectorStore {
    private val snippets = mutableListOf<DocumentSnippet>()

    fun clear() {
        snippets.clear()
    }

    fun addSnippet(snippet: DocumentSnippet) {
        snippets.add(snippet)
    }

    fun getAllSnippets(): List<DocumentSnippet> = snippets

    /**
     * Finds most semantically similar snippets based on cosine TF-IDF vector similarity of term matches.
     */
    fun search(query: String, maxResults: Int = 3): List<DocumentSnippet> {
        val queryTerms = getTerms(query)
        if (queryTerms.isEmpty() || snippets.isEmpty()) return emptyList()

        // Calculate relevance scores
        val scoredList = snippets.map { snippet ->
            val docTerms = getTerms(snippet.content)
            val score = cosineSimilarity(queryTerms, docTerms)
            Pair(snippet, score)
        }

        return scoredList
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    private fun getTerms(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9_]+"))
            .filter { it.length > 2 }
    }

    private fun cosineSimilarity(vec1: List<String>, vec2: List<String>): Double {
        val tf1 = vec1.groupingBy { it }.eachCount()
        val tf2 = vec2.groupingBy { it }.eachCount()

        val allTerms = tf1.keys + tf2.keys
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (term in allTerms) {
            val val1 = tf1[term]?.toDouble() ?: 0.0
            val val2 = tf2[term]?.toDouble() ?: 0.0
            dotProduct += val1 * val2
            norm1 += val1 * val1
            norm2 += val2 * val2
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }
}
