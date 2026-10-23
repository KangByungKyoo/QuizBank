package com.minipapa.quizbank.printing

import android.content.ContentResolver
import android.net.Uri
import com.minipapa.quizbank.data.BankRepository
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class GeneratedPdf(val file: File, val pages: Int, val kind: PrintDocumentKind)
data class GeneratedPrint(val selection: PrintSelection, val documents: List<GeneratedPdf>, val date: LocalDate = LocalDate.now()) {
    fun filename(document: GeneratedPdf): String {
        val category = selection.subtitle.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(60)
        return "QuizBank_${category}_${document.kind.fileLabel}_${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf"
    }
}

/** Owned by BankViewModel: rotation and navigating to preview keep the same random selection. */
class PrintExportController(private val repository: BankRepository, private val scope: CoroutineScope) {
    val options = MutableStateFlow(PrintOptions())
    val generated = MutableStateFlow<GeneratedPrint?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val savedUri = MutableStateFlow<Uri?>(null)

    fun update(value: PrintOptions) {
        if (busy.value || value == options.value) return
        options.value = value
        generated.value = null
    }

    fun prepare(cacheDir: File, ready: (GeneratedPrint) -> Unit) {
        if (busy.value) return
        generated.value?.let { ready(it); return }
        val settings = options.value
        work {
            val batch = withContext(Dispatchers.IO) {
                val selected = PrintSelector.select(repository.snapshot(), settings)
                val directory = File(cacheDir, "quiz-print/${UUID.randomUUID()}").apply { check(mkdirs()) { "PDF 작업 공간을 만들지 못했습니다." } }
                try {
                    val kinds = listOfNotNull(PrintDocumentKind.QUESTIONS, PrintDocumentKind.ANSWERS.takeIf { settings.includeAnswers })
                    val documents = kinds.map { kind ->
                        val file = File(directory, "${kind.name}.pdf")
                        GeneratedPdf(file, QuizPdfGenerator().generate(selected, kind, file), kind)
                    }
                    GeneratedPrint(selected, documents)
                } catch (e: Exception) {
                    directory.deleteRecursively()
                    throw e
                }
            }
            generated.value = batch
            ready(batch)
        }
    }

    fun save(resolver: ContentResolver, uri: Uri?, document: GeneratedPdf?) {
        if (uri == null) return // System picker cancellation is not an error.
        if (document == null) { message.value = "출력 파일을 다시 생성한 뒤 저장해 주세요."; return }
        work {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")?.use { output -> document.file.inputStream().use { it.copyTo(output) } }
                    ?: error("선택한 위치에 파일을 저장하지 못했습니다.")
            }
            savedUri.value = uri
            message.value = "${document.kind.label} PDF를 저장했습니다."
        }
    }

    private fun work(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "출력 작업을 완료하지 못했습니다: ${e.localizedMessage ?: "저장 공간과 파일 접근 권한을 확인해 주세요."}" }
            finally { busy.value = false }
        }
    }
}
