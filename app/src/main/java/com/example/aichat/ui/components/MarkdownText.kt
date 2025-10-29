package com.example.aichat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val nodes = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier) {
        nodes.forEach { node ->
            when (node) {
                is MarkdownNode.Heading -> Text(
                    text = node.content,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = (22 - node.level * 2).sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                is MarkdownNode.Paragraph -> Text(
                    text = node.buildAnnotatedString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                is MarkdownNode.CodeBlock -> Text(
                    text = node.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                )
                is MarkdownNode.Bullet -> Text(
                    text = "• ${node.content}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                is MarkdownNode.Link -> Text(
                    text = node.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable { uriHandler.openUri(node.url) }
                )
            }
        }
    }
}

@Composable
private fun MarkdownNode.Paragraph.buildAnnotatedString(): AnnotatedString = buildAnnotatedString {
    segments.forEach { segment ->
        when (segment) {
            is MarkdownSegment.Text -> append(segment.value)
            is MarkdownSegment.Code -> withStyle(
                SpanStyle(
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary
                )
            ) {
                append(segment.value)
            }
            is MarkdownSegment.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(segment.value)
            }
            is MarkdownSegment.Italic -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                append(segment.value)
            }
            is MarkdownSegment.Link -> {
                val annotationTag = "URL_${segment.url}"
                pushStringAnnotation(annotationTag, segment.url)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(segment.text)
                }
                pop()
            }
        }
    }
}

private object MarkdownParser {
    fun parse(input: String): List<MarkdownNode> {
        val lines = input.lines()
        val nodes = mutableListOf<MarkdownNode>()
        val codeBuffer = StringBuilder()
        var inCodeBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```") && !inCodeBlock) {
                inCodeBlock = true
                codeBuffer.clear()
                continue
            }
            if (trimmed.startsWith("```") && inCodeBlock) {
                nodes.add(MarkdownNode.CodeBlock(codeBuffer.toString()))
                codeBuffer.clear()
                inCodeBlock = false
                continue
            }

            if (inCodeBlock) {
                codeBuffer.appendLine(line)
                continue
            }

            when {
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val content = trimmed.drop(level).trim()
                    nodes.add(MarkdownNode.Heading(level, content))
                }
                trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                    nodes.add(MarkdownNode.Bullet(trimmed.drop(1).trim()))
                }
                trimmed.matches(Regex("\\[(.+)]\\((.+)\\)")) -> {
                    val match = Regex("\\[(.+)]\\((.+)\\)").find(trimmed)
                    if (match != null) {
                        nodes.add(MarkdownNode.Link(match.groupValues[1], match.groupValues[2]))
                    }
                }
                trimmed.isNotEmpty() -> nodes.add(MarkdownNode.Paragraph(parseSegments(trimmed)))
            }
        }

        if (codeBuffer.isNotEmpty()) {
            nodes.add(MarkdownNode.CodeBlock(codeBuffer.toString()))
        }
        return nodes
    }

    private fun parseSegments(line: String): List<MarkdownSegment> {
        val segments = mutableListOf<MarkdownSegment>()
        var remaining = line
        val codeRegex = Regex("`([^`]+)`")
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        val italicRegex = Regex("\\*([^*]+)\\*")
        val linkRegex = Regex("\\[(.+?)]\\((.+?)\\)")

        var index = 0
        while (index < remaining.length) {
            val codeMatch = codeRegex.find(remaining, index)
            val boldMatch = boldRegex.find(remaining, index)
            val italicMatch = italicRegex.find(remaining, index)
            val linkMatch = linkRegex.find(remaining, index)

            val nextMatch = listOfNotNull(codeMatch, boldMatch, italicMatch, linkMatch)
                .minByOrNull { it.range.first }

            if (nextMatch == null) {
                segments.add(MarkdownSegment.Text(remaining.substring(index)))
                break
            }

            if (nextMatch.range.first > index) {
                segments.add(MarkdownSegment.Text(remaining.substring(index, nextMatch.range.first)))
            }

            when (nextMatch) {
                codeMatch -> segments.add(MarkdownSegment.Code(nextMatch.groupValues[1]))
                boldMatch -> segments.add(MarkdownSegment.Bold(nextMatch.groupValues[1]))
                italicMatch -> segments.add(MarkdownSegment.Italic(nextMatch.groupValues[1]))
                linkMatch -> segments.add(MarkdownSegment.Link(nextMatch.groupValues[1], nextMatch.groupValues[2]))
            }
            index = nextMatch.range.last + 1
        }
        return segments
    }
}

private sealed interface MarkdownNode {
    data class Heading(val level: Int, val content: String) : MarkdownNode
    data class Paragraph(val segments: List<MarkdownSegment>) : MarkdownNode
    data class CodeBlock(val content: String) : MarkdownNode
    data class Bullet(val content: String) : MarkdownNode
    data class Link(val text: String, val url: String) : MarkdownNode
}

private sealed interface MarkdownSegment {
    data class Text(val value: String) : MarkdownSegment
    data class Code(val value: String) : MarkdownSegment
    data class Bold(val value: String) : MarkdownSegment
    data class Italic(val value: String) : MarkdownSegment
    data class Link(val text: String, val url: String) : MarkdownSegment
}
