package com.minipapa.quizbank

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.minipapa.quizbank.data.*
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*
import com.minipapa.quizbank.ui.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppFlowTest {
    @Test fun longAiReviewUsesAtMostTenVisibleLinesAndKeepsMissingTermFirst() {
        val review = AnswerReview(ReviewVerdict.PARTIAL, "아주 긴 검토 내용입니다.\n".repeat(30), listOf("이미지"), "설명을 보완합니다.\n".repeat(30))
        rule.setContent { QuizTheme { AiReviewSummary(review) } }
        rule.onNodeWithTag("ai-feedback").assertTextContains("보완할 용어: 이미지", substring = true)
        rule.onNodeWithTag("ai-feedback").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(getLayout(layouts))
            assertTrue(layouts.single().lineCount <= 10)
            assertTrue(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
        }
    }
    @Test fun aiPartialFeedbackShowsMissingConceptAndPersistsInHistory(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            repo.saveCategory(Category("work", "회사 업무"))
            val q = Question("docker", "work", QuestionType.SHORT, "컨테이너를 만드는 읽기 전용 템플릿은?", answer = "Docker 이미지", explanation = "원본 해설")
            repo.saveQuestion(q)
            val partial = AnswerReview(ReviewVerdict.PARTIAL, "Docker는 맞지만 이미지가 빠졌습니다.", listOf("이미지"), "이미지는 컨테이너 생성에 필요한 파일과 설정의 템플릿입니다.")
            val run = ExamRun(items = listOf(ExamItem(q, "회사 업무")), aiReviewEnabled = true)
            val model = BankViewModel(repo, SavedStateHandle(mapOf("exam" to JsonCodec.exam(run), "page" to "EXAM")),
                AnswerReviewer { question, input -> assertEquals(q, question); assertEquals("도커", input); partial })
            model.loaded.first { it }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            rule.onNodeWithTag("exam-input").performTextInput("도커")
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.exam.value?.responses?.firstOrNull()?.review != null }
            rule.onNodeWithText("일부 맞았습니다").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("보완할 용어: 이미지", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(partial, repo.draft()!!.responses.single().review)
            rule.onNodeWithTag("next-question").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.page.value == Page.RESULTS }
            val bank = repo.snapshot()
            assertEquals(0, bank.sessions.single().correct)
            assertTrue(bank.answers.single().explanation.contains(partial.rationale))
            assertTrue(bank.answers.single().explanation.startsWith("원본 해설"))
            assertEquals(bank, JsonCodec.readBackup(JsonCodec.backup(bank)))
        } finally { db.close() }
    }
    @Test fun failedAiReviewKeepsAnswerAndRequiresExplicitFallback(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            val q = Question("docker", "work", QuestionType.SHORT, "템플릿은?", answer = "Docker 이미지")
            val run = ExamRun(items = listOf(ExamItem(q, "회사 업무")), aiReviewEnabled = true)
            val model = BankViewModel(repo, SavedStateHandle(mapOf("exam" to JsonCodec.exam(run), "page" to "EXAM")),
                AnswerReviewer { _, _ -> throw IllegalStateException("테스트 연결 실패") })
            model.loaded.first { it }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            rule.onNodeWithTag("exam-input").performTextInput("도커")
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.exam.value?.answered == true && !model.busy.value }
            assertEquals("도커", repo.draft()!!.responses.single().input)
            assertTrue(model.exam.value!!.pendingReview)
            rule.onNodeWithTag("next-question").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithText("기본 채점으로 진행").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.exam.value?.pendingReview == false }
            rule.onNodeWithTag("next-question").performScrollTo().assertIsEnabled()
        } finally { db.close() }
    }
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: context.getExternalFilesDir("screenshots")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun registerQuestionAndRunFilteredExamThroughScreens(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            repo.saveCategory(Category("IT", "IT")); repo.saveCategory(Category("Python", "Python", "IT"))
            val model = BankViewModel(repo, SavedStateHandle())
            model.loaded.first { it }; model.bank.first { it.categories.size == 2 }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            screenshot("home")
            rule.onNodeWithText("＋ 문제 등록").performScrollTo().performClick()
            rule.onNodeWithText("카테고리: 선택해 주세요").performScrollTo().performClick()
            rule.onNodeWithText("IT/Python").performClick()
            rule.onNodeWithTag("question-content").performScrollTo().performTextInput("Python의 길이 함수는?")
            rule.onNodeWithText("객관식").assertIsNotEnabled()
            rule.onNodeWithText("O/X형").assertIsNotEnabled()
            rule.onNodeWithText("단답형").assertIsSelected()
            rule.onNodeWithTag("short-answer").performScrollTo().performTextInput("len()")
            rule.onNodeWithTag("save-question").performScrollTo().performClick()
            try { rule.waitUntil(10_000) { model.page.value == Page.QUESTIONS && model.bank.value.questions.size == 1 } }
            catch (e: Exception) {
                screenshot("registration-failure")
                throw AssertionError("page=${model.page.value}, busy=${model.busy.value}, message=${model.message.value}, questions=${model.bank.value.questions.size}\n${rule.onRoot().printToString()}", e)
            }
            rule.onNodeWithText("Python의 길이 함수는?").performScrollTo().assertIsDisplayed()
            rule.runOnIdle { model.navigate(Page.EXAM_SETUP) }
            rule.onNodeWithText("요청한 5개보다 문제가 적어 가능한 1개로 출제합니다.").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("start-exam").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.page.value == Page.EXAM }
            rule.onNodeWithTag("exam-input").performScrollTo().performTextInput("len()")
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.onNodeWithText("정답: len()").performScrollTo().assertIsDisplayed()
            screenshot("answer")
            rule.onNodeWithTag("next-question").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.page.value == Page.RESULTS }
            rule.onNodeWithText("정답 1개 · 오답 0개 · 총 1개").assertIsDisplayed()
            assertEquals(1, repo.snapshot().sessions.size)
        } finally { db.close() }
    }
    @Test fun allQuestionTypesManualOverrideAndRestoredExamSaveCorrectStatistics(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            val category = Category("c", "테스트")
            repo.saveCategory(category)
            val questions = listOf(
                Question("mc", "c", QuestionType.MULTIPLE, "4의 10의 보수는?", listOf("5", "6", "7"), "1", "4 + 6 = 10"),
                Question("ox", "c", QuestionType.OX, "1km는 1000m이다.", answer = "O"),
                Question("short", "c", QuestionType.SHORT, "대한민국 수도는?", answer = "서울", explanation = "서울입니다."))
            questions.forEach { repo.saveQuestion(it) }
            val run = ExamRun(items = questions.map { ExamItem(it, "테스트") })
            val handle = SavedStateHandle(mapOf("exam" to JsonCodec.exam(run), "page" to "EXAM"))
            val model = BankViewModel(repo, handle)
            model.loaded.first { it }
            rule.setContent { QuizTheme { QuizBankApp(model) } }
            screenshot("exam")
            rule.onNodeWithTag("exam-option-0").performScrollTo().performClick()
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.onNodeWithText("정답: 6").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("next-question").performScrollTo().performClick()
            rule.waitUntil(5_000) { model.exam.value!!.index == 1 }
            val restored = BankViewModel(repo, handle)
            restored.loaded.first { it }
            assertEquals(model.exam.value, restored.exam.value)
            rule.onNodeWithTag("exam-O").performScrollTo().performClick()
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.onNodeWithTag("next-question").performScrollTo().performClick()
            rule.waitUntil(5_000) { model.exam.value!!.index == 2 }
            rule.onNodeWithTag("exam-input").performScrollTo().performTextInput("서울특별시")
            rule.onNodeWithTag("submit-answer").performScrollTo().performClick()
            rule.onNodeWithText("정답으로 변경").performScrollTo().performClick()
            rule.onNodeWithTag("next-question").performScrollTo().performClick()
            rule.waitUntil(10_000) { model.page.value == Page.RESULTS }
            rule.onNodeWithText("정답 2개 · 오답 1개 · 총 3개").assertIsDisplayed()
            screenshot("results")
            val bank = repo.snapshot()
            assertEquals(2, bank.sessions.single().correct); assertEquals(1, bank.wrongCount("mc"))
            assertEquals(0, bank.wrongCount("short"))
            rule.onNodeWithText("오답 노트 열기").performScrollTo().performClick()
            rule.onNodeWithText("4의 10의 보수는?").assertIsDisplayed()
        } finally { db.close() }
    }
    @Test fun utf8FileImportExportAndRestoreUseContentResolver(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        val folder = File(context.cacheDir, "file-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val repo = BankRepository(db); val model = BankViewModel(repo, SavedStateHandle())
            model.loaded.first { it }
            val input = File(folder, "문제.csv").apply { writeText(CsvImport.header.joinToString(",") + "\n회사/업무,SHORT,한글 문제,,,,,,정답,해설", Charsets.UTF_8) }
            model.readCsv(context.contentResolver, Uri.fromFile(input))
            withTimeout(10_000) { model.csv.filterNotNull().first() }
            model.importCsv()
            withTimeout(10_000) { model.bank.first { it.questions.size == 1 } }
            val output = File(folder, "백업.json")
            model.export(context.contentResolver, Uri.fromFile(output))
            withTimeout(10_000) { model.message.filterNotNull().first { it.contains("백업했습니다") } }
            assertEquals("한글 문제", JsonCodec.readBackup(output.readText(Charsets.UTF_8)).questions.single().content)
            model.readBackup(context.contentResolver, Uri.fromFile(output))
            withTimeout(10_000) { model.backup.filterNotNull().first() }
            model.restore(RestoreMode.MERGE)
            withTimeout(10_000) { model.message.filterNotNull().first { it.contains("복원을 완료") } }
            assertEquals(1, repo.snapshot().questions.size)
        } finally { db.close(); folder.deleteRecursively() }
    }
}
