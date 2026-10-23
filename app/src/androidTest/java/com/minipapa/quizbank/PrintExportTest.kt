package com.minipapa.quizbank

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.print.PrintAttributes
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.minipapa.quizbank.data.*
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*
import com.minipapa.quizbank.printing.*
import com.minipapa.quizbank.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.random.Random

class PrintExportTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        ?: context.getExternalFilesDir("print-tests")!!.absolutePath).apply { mkdirs() }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList()
        else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }

    private fun systemWindow(packagePart: String): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(20_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains(packagePart) == true }
        return automation.rootInActiveWindow!!
    }

    @Test fun systemFilePickerSavesReopenablePdfAndPrintServiceShowsPreview(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        var saved: android.net.Uri? = null
        try {
            val repo = BankRepository(db)
            val source = bank(10)
            source.categories.forEach { repo.saveCategory(it) }; source.questions.forEach { repo.saveQuestion(it) }
            val original = repo.snapshot()
            val model = BankViewModel(repo, SavedStateHandle())
            model.loaded.first { it }; model.bank.first { it.questions.size == 10 }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            rule.runOnIdle { model.navigate(Page.PRINT) }
            rule.onNodeWithTag("print-save").performClick()
            rule.waitUntil(30_000) { model.printExport.generated.value != null && !model.printExport.busy.value }
            rule.onNodeWithText("문제지 · 2페이지").performClick()
            val picker = systemWindow("documentsui")
            val filename = "QuizBank_test_${UUID.randomUUID()}.pdf"
            val edit = nodes(picker).first { it.className?.toString() == "android.widget.EditText" }
            assertTrue(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
            }))
            val saveButton = nodes(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow).first {
                it.isEnabled && (it.viewIdResourceName?.endsWith("action_menu_save") == true || it.text?.toString() in listOf("저장", "Save", "SAVE"))
            }
            assertTrue(saveButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            rule.waitUntil(20_000) { model.printExport.savedUri.value != null && !model.printExport.busy.value }
            saved = model.printExport.savedUri.value!!
            val first = model.printExport.generated.value!!
            val reopened = context.contentResolver.openInputStream(saved!!)!!.use { it.readBytes() }
            assertArrayEquals(first.documents.first().file.readBytes(), reopened)
            File(output, "saf-reopened.pdf").writeBytes(reopened)
            rule.onNodeWithTag("print-start").performClick()
            rule.onNodeWithText("문제지 · 2페이지").performClick()
            val printWindow = systemWindow("printspooler")
            File(output, "system-print-window.txt").writeText(nodes(printWindow).joinToString("\n") { "${it.viewIdResourceName}: ${it.text} ${it.contentDescription}" })
            // Wait for the adapter's PDF to be displayed by the system print service.
            rule.waitUntil(20_000) {
                nodes(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow).any {
                    it.contentDescription?.toString()?.let { text -> text.contains("페이지") || text.contains("page", true) } == true
                }
            }
            rule.waitUntil(30_000) {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                var dark = 0
                for (y in bitmap.height / 4 until bitmap.height * 7 / 10 step 3) {
                    for (x in bitmap.width / 6 until bitmap.width * 5 / 6 step 3) {
                        val pixel = bitmap.getPixel(x, y)
                        if (android.graphics.Color.red(pixel) < 90 && android.graphics.Color.green(pixel) < 90 && android.graphics.Color.blue(pixel) < 90) dark++
                    }
                }
                bitmap.recycle()
                dark > 200
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(output, "system-print-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertEquals(original, repo.snapshot())
        } finally {
            saved?.let { android.provider.DocumentsContract.deleteDocument(context.contentResolver, it) }
            db.close()
        }
    }

    private fun bank(count: Int): Bank {
        val questions = (1..count).map { index ->
            val type = QuestionType.entries[(index - 1) % 3]
            val content = when (type) {
                QuestionType.SHORT -> "새 아이디어나 기술의 실현 가능성을 확인하는 개념 검증은? [$index]"
                QuestionType.MULTIPLE -> "Android 앱 개발에 사용하는 프로그래밍 언어는 무엇인가요? [$index]"
                QuestionType.OX -> "Git은 소스 코드의 변경 이력을 관리하는 버전 관리 도구이다. [$index]"
            }
            Question("q$index", "it", type, content,
                if (type == QuestionType.MULTIPLE) listOf("Python", "Kotlin", "JavaScript", "C++") else emptyList(),
                if (type == QuestionType.MULTIPLE) "1" else if (type == QuestionType.OX) "O" else "PoC",
                if (index % 5 == 0) "" else "한글과 English, 숫자 123, 특수문자 < > & / %를 함께 검증합니다.\n등록된 해설만 출력합니다.", createdAt = index.toLong(), updatedAt = index.toLong())
        }
        return Bank(listOf(Category("it", "IT 기술 용어")), questions)
    }

    private fun render(file: File, prefix: String): Pair<Int, String> {
        val text = StringBuilder()
        var count = 0
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                count = renderer.pageCount
                repeat(count) { index -> renderer.openPage(index).use { page ->
                    assertEquals(595, page.width); assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    File(output, "$prefix-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    if (Build.VERSION.SDK_INT >= 35) {
                        val content = page.textContents.joinToString("\n") { it.text }
                        assertTrue("Missing footer on $prefix page $index: $content", content.replace(Regex("\\s"), "").contains("${index + 1}/$count"))
                        text.append(content).append('\n')
                    }
                } }
            }
        }
        File(output, "$prefix.txt").writeText(text.toString())
        return count to text.toString()
    }

    @Test fun tenAndSixtyMixedQuestionsGenerateReadableA4WithMatchingAnswers(): Unit = runBlocking {
        val source = bank(60)
        val options = PrintOptions(count = 10, answerLines = 2, randomOrder = true)
        val selection = PrintSelector.select(source, options, Random(18))
        val questions = File(output, "QuizBank_sample_questions.pdf")
        val answers = File(output, "QuizBank_sample_answers.pdf")
        val questionPages = QuizPdfGenerator().generate(selection, PrintDocumentKind.QUESTIONS, questions)
        val answerPages = QuizPdfGenerator().generate(selection, PrintDocumentKind.ANSWERS, answers)
        val renderedQ = render(questions, "sample-questions")
        val renderedA = render(answers, "sample-answers")
        assertEquals(questionPages, renderedQ.first); assertEquals(answerPages, renderedA.first)
        if (Build.VERSION.SDK_INT >= 35) {
            assertTrue(renderedQ.second.contains("문제 은행")); assertTrue(renderedA.second.contains("정답 및 해설"))
            assertTrue(renderedA.second.contains("Kotlin")); assertTrue(renderedA.second.contains("해설 미등록"))
            assertFalse(renderedQ.second.contains("정답: PoC"))
            selection.questions.forEach { q ->
                assertTrue(renderedQ.second.replace(Regex("\\s"), "").contains(q.content.replace(Regex("\\s"), "")))
                assertTrue(renderedA.second.contains(q.answerText))
            }
        }
        val all = PrintSelector.select(source, options.copy(count = 0, randomOrder = false))
        val sixty = File(output, "sixty-questions.pdf")
        assertTrue(QuizPdfGenerator().generate(all, PrintDocumentKind.QUESTIONS, sixty) > 1)
        render(sixty, "sixty-questions")
        val noExplanations = all.copy(options = all.options.copy(includeExplanations = false))
        val answerOnly = File(output, "answer-only.pdf")
        QuizPdfGenerator().generate(noExplanations, PrintDocumentKind.ANSWERS, answerOnly)
        val text = render(answerOnly, "answer-only").second
        if (Build.VERSION.SDK_INT >= 35) assertFalse(text.contains("특수문자"))
        assertEquals(source, bank(60))
    }

    @Test fun extremelyLongQuestionsOptionsAndExplanationsContinueWithoutLosingTail(): Unit = runBlocking {
        val long = bank(1).questions.single().copy(content = "긴 문제 한글 English 줄바꿈 검증. ".repeat(700) + "문제마지막표식",
            options = listOf("긴 보기 ".repeat(300) + "보기마지막표식", "두 번째"),
            explanation = "긴 해설을 안전하게 다음 페이지로 이어서 출력합니다. ".repeat(800) + "해설마지막표식")
        val selection = PrintSelection(listOf(long), "긴 문서 검증", PrintOptions(answerLines = 3))
        val q = File(output, "long-question.pdf"); val a = File(output, "long-answer.pdf")
        assertTrue(QuizPdfGenerator().generate(selection, PrintDocumentKind.QUESTIONS, q) > 1)
        assertTrue(QuizPdfGenerator().generate(selection, PrintDocumentKind.ANSWERS, a) > 1)
        val qt = render(q, "long-question").second; val at = render(a, "long-answer").second
        if (Build.VERSION.SDK_INT >= 35) {
            assertTrue(qt.contains("문제마지막표식")); assertTrue(qt.contains("보기마지막표식")); assertTrue(at.contains("해설마지막표식"))
        }
        val subset = File(output, "print-page-two.pdf")
        QuizPdfGenerator().generate(selection, PrintDocumentKind.QUESTIONS, subset, setOf(1),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
        ParcelFileDescriptor.open(subset, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { assertEquals(1, it.pageCount) } }
    }

    @Test fun settingsPreviewAndSafCopyKeepRoomDataAndSelectionUnchanged(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            val source = bank(10)
            source.categories.forEach { repo.saveCategory(it) }; source.questions.forEach { repo.saveQuestion(it) }
            repo.complete(ExamRun(items = listOf(ExamItem(source.questions.first(), "IT 기술 용어")), responses = listOf(ExamResponse("1", true))))
            val original = repo.snapshot()
            val model = BankViewModel(repo, SavedStateHandle())
            model.loaded.first { it }; model.bank.first { it.questions.size == 10 }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            rule.runOnIdle { model.navigate(Page.PROBLEMS) }
            rule.onNodeWithText("문제 출력").performClick()
            rule.onNodeWithTag("print-preview").performClick()
            rule.waitUntil(30_000) { model.page.value == Page.PRINT_PREVIEW && model.printExport.generated.value != null }
            rule.onNodeWithTag("pdf-preview-page-0").assertIsDisplayed()
            val first = model.printExport.generated.value!!
            rule.onNodeWithText("정답·해설지 (${first.documents[1].pages})").performClick()
            rule.waitForIdle()
            File(output, "print-preview.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            rule.runOnIdle { model.back() }
            rule.onNodeWithTag("print-preview").performClick()
            assertSame(first, model.printExport.generated.value)
            val destination = File(context.cacheDir, "saved-print.pdf")
            rule.runOnIdle { model.printExport.save(context.contentResolver, android.net.Uri.fromFile(destination), first.documents.first()) }
            rule.waitUntil(10_000) { !model.printExport.busy.value }
            assertArrayEquals(first.documents.first().file.readBytes(), destination.readBytes())
            rule.runOnIdle { model.printExport.save(context.contentResolver, null, first.documents.first()) }
            assertEquals(original, repo.snapshot())
            rule.runOnIdle {
                model.printExport.update(model.printExport.options.value.copy(includeAnswers = false))
                model.printExport.prepare(context.cacheDir) { }
            }
            rule.waitUntil(20_000) { model.printExport.generated.value != null && !model.printExport.busy.value }
            assertEquals(listOf(PrintDocumentKind.QUESTIONS), model.printExport.generated.value!!.documents.map { it.kind })
            val invalid = android.net.Uri.fromFile(File(context.cacheDir, "missing-${UUID.randomUUID()}/output.pdf"))
            rule.runOnIdle { model.printExport.save(context.contentResolver, invalid, first.documents.first()) }
            rule.waitUntil(10_000) { !model.printExport.busy.value }
            assertTrue(model.printExport.message.value!!.contains("완료하지 못했습니다"))
            assertEquals(original, repo.snapshot())
        } finally { db.close() }
    }
}
