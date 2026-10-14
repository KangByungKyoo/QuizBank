package com.minipapa.quizbank.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class CsvRow(val line: Int, val fields: List<String>)
data class ImportQuestion(val line: Int, val categoryPath: String, val question: Question)
data class ImportPreview(val questions: List<ImportQuestion>, val errors: List<String>)
object CsvImport {
    val header = listOf("categoryPath", "type", "question", "option1", "option2", "option3", "option4", "option5", "answer", "explanation")
    fun decode(bytes: ByteArray): String {
        fun strict(charset: Charset) = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        try { return strict(Charsets.UTF_8) }
        catch (e: CharacterCodingException) {
            // A UTF-8 BOM declares the encoding; do not reinterpret damaged UTF-8 as CP949.
            val utf8Bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            if (!utf8Bom) {
                try { return strict(Charset.forName("MS949")) }
                catch (_: CharacterCodingException) { /* Report an actionable error below. */ }
            }
            throw IllegalArgumentException("CSV 파일의 문자 인코딩을 읽을 수 없습니다. Excel에서 ‘CSV UTF-8(쉼표로 분리)’ 형식으로 다시 저장해 주세요.", e)
        }
    }
    fun rows(source: String): List<CsvRow> {
        val text = source.removePrefix("\uFEFF")
        val result = mutableListOf<CsvRow>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false; var closed = false; var line = 1; var start = 1; var i = 0
        fun finishRow() {
            fields.add(field.toString()); field.clear()
            if (fields.any { it.isNotEmpty() }) result.add(CsvRow(start, fields.toList()))
            fields.clear(); closed = false
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else {
                    field.append(c)
                    if (c == '\n' || (c == '\r' && (i + 1 == text.length || text[i + 1] != '\n'))) line++
                }
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closed) { "${line}행: 따옴표 위치가 올바르지 않습니다." }; quoted = true }
                ',' -> { fields.add(field.toString()); field.clear(); closed = false }
                '\r', '\n' -> {
                    finishRow()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    line++; start = line
                }
                else -> { require(!closed) { "${line}행: 닫는 따옴표 뒤에 문자가 있습니다." }; field.append(c) }
            }
            i++
        }
        require(!quoted) { "${start}행: 따옴표가 닫히지 않았습니다." }
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) finishRow()
        return result
    }
    fun preview(text: String): ImportPreview {
        val rows = runCatching { rows(text) }.getOrElse { return ImportPreview(emptyList(), listOf(it.message ?: "CSV 오류")) }
        if (rows.isEmpty() || rows.first().fields != header) return ImportPreview(emptyList(), listOf("첫 행의 헤더는 ${header.joinToString(",")} 순서여야 합니다."))
        val valid = mutableListOf<ImportQuestion>(); val errors = mutableListOf<String>()
        rows.drop(1).forEach { row ->
            runCatching {
                require(row.fields.size == header.size) { "열 개수는 10개여야 합니다." }
                val f = row.fields
                val path = f[0].split('/').map { it.trim() }
                require(path.all { it.isNotBlank() }) { "카테고리 경로가 비어 있습니다." }
                val type = when (f[1].trim().uppercase()) {
                    "MULTIPLE", "MCQ", "객관식" -> QuestionType.MULTIPLE
                    "OX", "O/X", "O/X형" -> QuestionType.OX
                    "SHORT", "단답형" -> QuestionType.SHORT
                    else -> error("문제 유형은 MULTIPLE, OX, SHORT를 사용하세요.")
                }
                val options = f.subList(3, 8).dropLastWhile { it.isEmpty() }
                val answer = when (type) {
                    QuestionType.MULTIPLE -> {
                        val numeric = f[8].trim().toIntOrNull()
                        if (numeric != null) (numeric - 1).toString() else {
                            require(options.count { it == f[8] } == 1) { "정답은 보기 번호(1~5) 또는 유일한 보기 내용이어야 합니다." }
                            options.indexOf(f[8]).toString()
                        }
                    }
                    QuestionType.OX -> f[8].trim().uppercase()
                    QuestionType.SHORT -> f[8]
                }
                val q = Question(categoryId = "pending", type = type, content = f[2],
                    options = if (type == QuestionType.MULTIPLE) options else emptyList(), answer = answer, explanation = f[9])
                q.validate()
                valid.add(ImportQuestion(row.line, path.joinToString("/"), q))
            }.onFailure { errors.add("${row.line}행: ${it.message}") }
        }
        return ImportPreview(valid, errors)
    }
}
