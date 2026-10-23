package com.minipapa.quizbank.printing

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/** Reuses the frozen selection, including when the system requests a subset of pages. */
class QuizPrintAdapter(private val batch: GeneratedPrint, private val document: GeneratedPdf, private val cacheDir: File) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var attributes: PrintAttributes? = null

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        attributes = newAttributes
        callback.onLayoutFinished(PrintDocumentInfo.Builder(batch.filename(document))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(document.pages).build(), oldAttributes != newAttributes)
    }

    override fun onWrite(ranges: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
        val selected = (0 until document.pages).filter { page -> ranges.any { page in it.start..it.end } }.toSet()
        if (selected.isEmpty()) { callback.onWriteFailed("선택한 페이지가 없습니다."); return }
        val printAttributes = attributes
        val job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val temporary = File.createTempFile("quiz-print-", ".pdf", cacheDir)
                    try {
                        QuizPdfGenerator().generate(batch.selection, document.kind, temporary, selected, printAttributes)
                        FileOutputStream(destination.fileDescriptor).use { output -> temporary.inputStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        } }
                    } finally { temporary.delete() }
                }
                callback.onWriteFinished(selected.sorted().map { PageRange(it, it) }.toTypedArray())
            } catch (e: CancellationException) { callback.onWriteCancelled() }
            catch (e: Exception) { callback.onWriteFailed(e.localizedMessage ?: "인쇄 문서를 만들지 못했습니다.") }
            finally { cancellationSignal.setOnCancelListener(null) }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onFinish() { scope.cancel() }
}
