package io.github.nilsmorczinietz.nod.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType
import io.github.nilsmorczinietz.nod.ui.components.CodeBlock

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val marker: String, val text: String, val indent: Int) : Block
    data class Code(val text: String) : Block
    data class Paragraph(val text: String) : Block
}

private val bulletRegex = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")

private fun parse(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = markdown.lines()
    var i = 0
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) code.appendLine(lines[i++])
                blocks += Block.Code(code.toString().trimEnd())
            }
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += Block.Heading(level, line.drop(level).trim())
            }
            bulletRegex.matches(line) -> {
                flush()
                val (indent, marker, text) = bulletRegex.find(line)!!.destructured
                blocks += Block.Bullet(if (marker.first().isDigit()) marker else "•", text, indent.length / 2)
            }
            line.isBlank() -> flush()
            else -> paragraph.append(line).append(' ')
        }
        i++
    }
    flush()
    return blocks
}

/** Inline `code` and **bold**. */
@Composable
private fun inline(text: String): AnnotatedString {
    val codeBackground = NodTheme.colors.muted
    val mono = NodType.code.fontFamily
    return remember(text, codeBackground, mono) {
        buildAnnotatedString {
            var rest = text
            val token = Regex("""`([^`]+)`|\*\*([^*]+)\*\*""")
            while (true) {
                val match = token.find(rest) ?: break
                append(rest.substring(0, match.range.first))
                val (code, bold) = match.destructured
                if (code.isNotEmpty()) {
                    withStyle(SpanStyle(fontFamily = mono, background = codeBackground)) { append(code) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                }
                rest = rest.substring(match.range.last + 1)
            }
            append(rest)
        }
    }
}

/** Lightweight Markdown renderer for plans: headings, lists, code blocks, inline code/bold. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    val blocks = remember(markdown) { parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Text(
                    inline(block.text),
                    style = if (block.level <= 2) NodType.heading else NodType.small,
                    color = colors.foreground,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Bullet -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(block.marker, color = colors.mutedForeground, style = NodType.body, modifier = Modifier.width(22.dp))
                    Text(inline(block.text), color = colors.foreground, style = NodType.body)
                }
                is Block.Code -> CodeBlock(block.text)
                is Block.Paragraph -> Text(inline(block.text), color = colors.foreground, style = NodType.body)
            }
        }
    }
}
