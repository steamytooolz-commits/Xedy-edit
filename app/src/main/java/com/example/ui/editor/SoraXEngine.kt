package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

data class DiagnosticIssue(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: Severity
) {
    enum class Severity { ERROR, WARNING, INFO }
}

object SoraXEngine {

    // Simulates an LSP (Language Server Protocol) server providing diagnostic issues
    fun runLSPDiagnostics(code: String, language: String): List<DiagnosticIssue> {
        val diagnostics = mutableListOf<DiagnosticIssue>()
        val lines = code.lines()

        for (i in lines.indices) {
            val lineText = lines[i].trim()
            
            // Simulating basic syntactic diagnostic issues
            if (language == "python") {
                if (lineText.startsWith("def ") && !lineText.endsWith(":")) {
                    diagnostics.add(DiagnosticIssue(i + 1, lineText.length, "SyntaxError: expected ':' at end of function declaration", DiagnosticIssue.Severity.ERROR))
                }
                if (lineText.contains("print ") && !lineText.contains("print(")) {
                    diagnostics.add(DiagnosticIssue(i + 1, 1, "Python 3 warning: print is a function, use print(...)", DiagnosticIssue.Severity.WARNING))
                }
            } else if (language == "javascript") {
                if (lineText.startsWith("const ") && !lineText.contains("=") && !lineText.endsWith(";")) {
                    diagnostics.add(DiagnosticIssue(i + 1, lineText.length, "LSP: const declaration must be initialized", DiagnosticIssue.Severity.ERROR))
                }
            } else if (language == "kotlin") {
                if (lineText.startsWith("fun ") && !lineText.contains("(") && !lineText.contains(")")) {
                    diagnostics.add(DiagnosticIssue(i + 1, lineText.length, "SyntaxError: fun declaration must have parameter list", DiagnosticIssue.Severity.ERROR))
                }
            }
        }
        return diagnostics
    }

    // High performance syntax text highlighter mapping TextMate Grammar scopes
    fun parseTextMateGrammars(code: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            if (code.isEmpty()) return@buildAnnotatedString

            // Define scopes and matching patterns matching TextMate style
            val scopes = when (language) {
                "python" -> listOf(
                    ScopePattern("\\b(def|class|return|if|else|elif|for|while|import|from|as|in|try|except|pass|with|lambda)\\b", SpanStyle(color = Color(0xFFC792EA), fontWeight = FontWeight.Bold)), // keywords
                    ScopePattern("\\b(print|len|range|int|str|float|list|dict|set|print|max|min)\\b", SpanStyle(color = Color(0xFF82AAFF))), // builtins
                    ScopePattern("\".*?\"|'.*?'", SpanStyle(color = Color(0xFFC3E88D))), // strings
                    ScopePattern("#.*", SpanStyle(color = Color(0xFF546E7A))), // comments
                    ScopePattern("\\b[0-9]+\\b", SpanStyle(color = Color(0xFFF78C6C))) // numbers
                )
                "javascript", "typescript" -> listOf(
                    ScopePattern("\\b(const|let|var|function|return|if|else|for|while|import|export|from|class|extends|new|async|await|require)\\b", SpanStyle(color = Color(0xFFC792EA), fontWeight = FontWeight.Bold)), // keywords
                    ScopePattern("\\b(console|log|require|module|exports|window|document)\\b", SpanStyle(color = Color(0xFF82AAFF))), // global variables
                    ScopePattern("\".*?\"|'.*?'|`.*?`", SpanStyle(color = Color(0xFFC3E88D))), // strings
                    ScopePattern("//.*|/\\*.*?\\*/", SpanStyle(color = Color(0xFF546E7A))), // comments
                    ScopePattern("\\b[0-9]+\\b", SpanStyle(color = Color(0xFFF78C6C))) // numbers
                )
                "kotlin", "java" -> listOf(
                    ScopePattern("\\b(package|import|class|interface|fun|val|var|return|if|else|for|while|when|is|in|object|private|public|protected|internal|override)\\b", SpanStyle(color = Color(0xFFC792EA), fontWeight = FontWeight.Bold)), // keywords
                    ScopePattern("\\b(println|print|Int|String|Boolean|Double|Float|List|Map|Set|listOf|mapOf|setOf)\\b", SpanStyle(color = Color(0xFF82AAFF))), // core libraries
                    ScopePattern("\".*?\"", SpanStyle(color = Color(0xFFC3E88D))), // strings
                    ScopePattern("//.*", SpanStyle(color = Color(0xFF546E7A))), // comments
                    ScopePattern("\\b[0-9]+\\b", SpanStyle(color = Color(0xFFF78C6C))) // numbers
                )
                "html" -> listOf(
                    ScopePattern("<!?/?\\w+.*?>", SpanStyle(color = Color(0xFFF07178))), // tags
                    ScopePattern("\".*?\"", SpanStyle(color = Color(0xFFC3E88D))), // values
                    ScopePattern("<!--.*?-->", SpanStyle(color = Color(0xFF546E7A))) // comments
                )
                else -> emptyList() // Fallback plain text
            }

            // Apply style overlays based on matches
            for (scope in scopes) {
                val matcher = Pattern.compile(scope.regex).matcher(code)
                while (matcher.find()) {
                    addStyle(scope.style, matcher.start(), matcher.end())
                }
            }

            // Apply red under-wavy line highlighting for LSP error diagnostics
            val lspIssues = runLSPDiagnostics(code, language)
            val lines = code.lines()
            for (issue in lspIssues) {
                if (issue.severity == DiagnosticIssue.Severity.ERROR) {
                    var cumulativeIndex = 0
                    for (i in 0 until (issue.line - 1)) {
                        if (i < lines.size) cumulativeIndex += lines[i].length + 1
                    }
                    val lineLength = if (issue.line - 1 < lines.size) lines[issue.line - 1].length else 0
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = Color(0xFFFF5370)
                        ),
                        cumulativeIndex,
                        cumulativeIndex + lineLength
                    )
                }
            }
        }
    }
}

private class ScopePattern(val regex: String, val style: SpanStyle)
