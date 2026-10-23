package com.minipapa.quizbank.printing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.print.PrintAttributes
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.max

enum class PrintDocumentKind(val label: String, val fileLabel: String) {
    QUESTIONS("문제지", "문제지"), ANSWERS("정답·해설지", "정답해설지")
}

/** A4 point coordinates; Android embeds the device's Korean font through Canvas text. */
class QuizPdfGenerator {
    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        private const val LEFT = 36f
        private const val RIGHT = 559f
        private const val BOTTOM = 784f
    }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10.5f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val bold = TextPaint(body).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE }
    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    private lateinit var selection: PrintSelection
    private lateinit var kind: PrintDocumentKind
    private var y = 0f

    private fun layout(text: String, width: Int, paint: TextPaint = body): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).setLineSpacing(2f, 1f).build()

    private fun text(value: String, x: Float, top: Float, width: Int, paint: TextPaint = body) {
        val block = layout(value, width, paint)
        chunk(block, 0, block.lineCount, x, top)
    }

    private fun chunk(block: StaticLayout, from: Int, to: Int, x: Float, top: Float) {
        if (from >= to) return
        val offset = block.getLineTop(from)
        val height = block.getLineBottom(to - 1) - offset
        pages.last().add { canvas ->
            canvas.save()
            canvas.clipRect(x, top, x + block.width, top + height)
            canvas.translate(x, top - offset)
            block.draw(canvas)
            canvas.restore()
        }
    }

    private fun rule(x1: Float, y1: Float, x2: Float, y2: Float) {
        pages.last().add { it.drawLine(x1, y1, x2, y2, line) }
    }

    private fun newPage() {
        pages.add(mutableListOf())
        val title = if (kind == PrintDocumentKind.QUESTIONS) "문제 은행" else "문제 은행 - 정답 및 해설"
        text(title, LEFT, 32f, 523, TextPaint(bold).apply { textSize = 18f })
        val subtitle = TextUtils.ellipsize(selection.subtitle, body, 523f, TextUtils.TruncateAt.END).toString()
        text(subtitle, LEFT, 61f, 523)
        text("총 ${selection.questions.size}문제 / ${selection.typeLabel}", LEFT, 80f, 523)
        if (kind == PrintDocumentKind.QUESTIONS) {
            text("이름: ____________________", LEFT, 105f, 260)
            text("날짜: ____________________", 310f, 105f, 249)
            rule(LEFT, 128f, RIGHT, 128f)
            y = 143f
        } else {
            y = 108f
            tableHeader()
        }
    }

    private fun tableHeader() {
        val top = y
        val boundaries = columns()
        val titles = if (selection.options.includeExplanations) listOf("번호", "정답", "해설") else listOf("번호", "정답")
        pages.last().add { canvas ->
            canvas.drawRect(LEFT, top, RIGHT, top + 26f, Paint().apply { color = Color.rgb(240, 240, 240) })
        }
        titles.forEachIndexed { index, title -> text(title, boundaries[index] + 7f, top + 6f, (boundaries[index + 1] - boundaries[index] - 14).toInt(), bold) }
        tableGrid(top, 26f)
        y += 26f
    }

    private fun columns() = if (selection.options.includeExplanations) listOf(LEFT, 76f, 221f, RIGHT) else listOf(LEFT, 76f, RIGHT)
    private fun tableGrid(top: Float, height: Float) {
        rule(LEFT, top, RIGHT, top); rule(LEFT, top + height, RIGHT, top + height)
        columns().forEach { rule(it, top, it, top + height) }
    }

    private fun fittingEnd(block: StaticLayout, from: Int, available: Float): Int {
        var end = from
        while (end < block.lineCount && block.getLineBottom(end) - block.getLineTop(from) <= available) end++
        return end
    }

    private fun question(number: Int, q: Question) {
        val content = buildString {
            append(q.content)
            if (q.type == QuestionType.MULTIPLE) q.options.forEachIndexed { i, option -> append("\n${listOf("①", "②", "③", "④", "⑤").getOrElse(i) { "${i + 1}." }} $option") }
        }
        val block = layout(content, 495)
        val answerHeight = if (q.type == QuestionType.SHORT) selection.options.answerLines * 23f + 8f else 28f
        val fullHeight = block.height + answerHeight + 16f
        if (y + fullHeight > BOTTOM && y > 143f) newPage()
        var start = 0
        var continued = false
        while (start < block.lineCount) {
            text("$number.", LEFT, y, 26, bold)
            if (continued) {
                text("(계속)", LEFT + 28f, y, 495, bold)
                y += 18f
            }
            val remaining = block.height - block.getLineTop(start)
            val finalChunk = remaining + answerHeight <= BOTTOM - y
            // Leave enough room for the final lines and their answer space together.
            val end = if (finalChunk) block.lineCount else fittingEnd(block, start, BOTTOM - y - answerHeight - 10f)
            if (end <= start) { newPage(); continue }
            val actualEnd = end
            if (actualEnd <= start) { newPage(); continue }
            chunk(block, start, actualEnd, LEFT + 28f, y)
            y += block.getLineBottom(actualEnd - 1) - block.getLineTop(start)
            start = actualEnd
            if (start < block.lineCount) { newPage(); continued = true }
        }
        if (y + answerHeight > BOTTOM) {
            newPage()
            text("$number. (답안 공간)", LEFT, y, 523, bold); y += 20f
        }
        when (q.type) {
            QuestionType.SHORT -> {
                text("정답:", LEFT + 28f, y + 9f, 35)
                repeat(selection.options.answerLines) { index -> rule(LEFT + 65f, y + 22f + index * 23f, RIGHT, y + 22f + index * 23f) }
                y += answerHeight
            }
            QuestionType.OX -> { text("( O / X )", LEFT + 28f, y + 8f, 495); y += answerHeight }
            QuestionType.MULTIPLE -> y += answerHeight
        }
        y += 16f
    }

    private fun answer(number: Int, q: Question) {
        val bounds = columns()
        val answer = if (q.type == QuestionType.MULTIPLE) "${q.answer.toInt() + 1}. ${q.answerText}" else q.answerText
        val values = if (selection.options.includeExplanations) listOf("$number", answer, q.explanation.ifBlank { "해설 미등록" }) else listOf("$number", answer)
        val blocks = values.mapIndexed { i, value -> layout(value, (bounds[i + 1] - bounds[i] - 14).toInt()) }
        val rowHeight = blocks.maxOf { it.height } + 16f
        if (y + rowHeight > BOTTOM && y > 134f) newPage()
        val starts = IntArray(blocks.size)
        while (blocks.indices.any { starts[it] < blocks[it].lineCount }) {
            val available = BOTTOM - y - 16f
            val ends = blocks.mapIndexed { i, block -> fittingEnd(block, starts[i], available) }
            if (blocks.indices.any { starts[it] < blocks[it].lineCount && ends[it] <= starts[it] }) { newPage(); continue }
            var height = 20f
            blocks.forEachIndexed { i, block ->
                if (starts[i] < ends[i]) {
                    chunk(block, starts[i], ends[i], bounds[i] + 7f, y + 8f)
                    height = max(height, (block.getLineBottom(ends[i] - 1) - block.getLineTop(starts[i]) + 16).toFloat())
                } else if (i == 0) text("$number", bounds[0] + 7f, y + 8f, 26)
            }
            tableGrid(y, height)
            y += height
            ends.forEachIndexed { i, end -> starts[i] = end }
            if (blocks.indices.any { starts[it] < blocks[it].lineCount }) newPage()
        }
    }

    suspend fun generate(selection: PrintSelection, kind: PrintDocumentKind, destination: File,
        pageIndices: Set<Int>? = null, attributes: PrintAttributes? = null): Int {
        this.selection = selection; this.kind = kind; pages.clear()
        require(selection.questions.isNotEmpty()) { "출력할 문제가 없습니다." }
        newPage()
        selection.questions.forEachIndexed { index, q ->
            currentCoroutineContext().ensureActive()
            if (kind == PrintDocumentKind.QUESTIONS) question(index + 1, q) else answer(index + 1, q)
        }
        val total = pages.size
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, commands ->
                currentCoroutineContext().ensureActive()
                if (pageIndices != null && index !in pageIndices) return@forEachIndexed
                val width = attributes?.mediaSize?.let { (it.widthMils * 72f / 1000).toInt() } ?: PAGE_WIDTH
                val height = attributes?.mediaSize?.let { (it.heightMils * 72f / 1000).toInt() } ?: PAGE_HEIGHT
                val margins = attributes?.minMargins
                val left = (margins?.leftMils ?: 0) * 72f / 1000
                val top = (margins?.topMils ?: 0) * 72f / 1000
                val availableWidth = width - left - (margins?.rightMils ?: 0) * 72f / 1000
                val availableHeight = height - top - (margins?.bottomMils ?: 0) * 72f / 1000
                require(availableWidth > 0 && availableHeight > 0) { "인쇄 여백이 용지보다 큽니다." }
                val scale = minOf(availableWidth / PAGE_WIDTH, availableHeight / PAGE_HEIGHT)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.translate(left + (availableWidth - PAGE_WIDTH * scale) / 2, top + (availableHeight - PAGE_HEIGHT * scale) / 2)
                page.canvas.scale(scale, scale)
                commands.forEach { it(page.canvas) }
                page.canvas.drawLine(LEFT, 801f, RIGHT, 801f, line)
                page.canvas.drawText("QuizBank · 문제 은행", LEFT, 817f, body)
                val footer = "${index + 1} / $total"
                page.canvas.drawText(footer, RIGHT - body.measureText(footer), 817f, body)
                pdf.finishPage(page)
            }
            destination.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        pages.clear()
        return total
    }
}
