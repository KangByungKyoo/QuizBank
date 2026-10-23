package com.minipapa.quizbank.navigation

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.*
import com.minipapa.quizbank.data.BankRepository
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.minipapa.quizbank.printing.PrintExportController

enum class Page(val title: String) {
    HOME("문제 은행"), PROBLEMS("문제"), LEARNING("학습"), CATEGORIES("카테고리 관리"), QUESTIONS("문제 관리"), EDIT("문제 등록"),
    EXAM_SETUP("시험 설정"), EXAM("문제 풀이"), RESULTS("시험 결과"), WRONG("오답 노트"), STATS("학습 통계"), SETTINGS("설정"),
    PRINT("문제 출력"), PRINT_PREVIEW("출력 미리보기")
}
class BankViewModel(val repository: BankRepository, private val saved: SavedStateHandle, private val reviewer: AnswerReviewer? = null) : ViewModel() {
    val printExport = PrintExportController(repository, viewModelScope)
    val message = MutableStateFlow<String?>(null)
    val bank = repository.bank.catch { message.value = "데이터를 읽지 못했습니다: ${it.message}" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Bank())
    val loaded = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val aiReviewing = MutableStateFlow(false)
    val page = MutableStateFlow(runCatching { Page.valueOf(saved["page"] ?: "HOME") }.getOrDefault(Page.HOME))
    val exam = MutableStateFlow(JsonCodec.readExam(saved["exam"]))
    val editing = MutableStateFlow<Question?>(null)
    val csv = MutableStateFlow<ImportPreview?>(null)
    val backup = MutableStateFlow<Bank?>(null)
    val homeCategory = saved.getStateFlow<String?>("homeCategory", null)
    fun selectHomeCategory(id: String?) { saved["homeCategory"] = id }
    fun back() = navigate(when (page.value) {
        Page.QUESTIONS, Page.CATEGORIES, Page.PRINT -> Page.PROBLEMS
        Page.PRINT_PREVIEW -> Page.PRINT
        Page.WRONG, Page.STATS -> Page.LEARNING
        else -> Page.HOME
    })
    private var returnPage = Page.QUESTIONS
    init {
        if (page.value == Page.EDIT) navigate(Page.HOME)
        viewModelScope.launch {
            try {
                repository.bank.first()
                val draft = repository.draft() ?: exam.value
                setExam(draft)
                if (page.value in listOf(Page.EXAM, Page.RESULTS) && draft == null) navigate(Page.HOME)
                loaded.value = true
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "데이터를 읽지 못했습니다: ${e.message}" }
        }
    }
    fun navigate(next: Page) { page.value = next; saved["page"] = next.name }
    private fun run(action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "작업을 완료하지 못했습니다." }
            finally { busy.value = false }
        }
    }
    fun edit(question: Question? = null) {
        returnPage = if (page.value == Page.WRONG) Page.WRONG else Page.QUESTIONS
        editing.value = question; navigate(Page.EDIT)
    }
    fun cancelEdit() { navigate(returnPage) }
    fun save(question: Question) = run {
        repository.saveQuestion(question); navigate(returnPage); message.value = "문제를 저장했습니다."
    }
    fun saveCategory(category: Category) = run { repository.saveCategory(category); message.value = "카테고리를 저장했습니다." }
    fun deleteCategory(id: String) = run { repository.deleteCategory(id); message.value = "카테고리와 포함된 문제를 삭제했습니다. 이전 시험 기록은 유지됩니다." }
    fun deleteQuestion(id: String) = run { repository.deleteQuestion(id); message.value = "문제를 삭제했습니다. 이전 시험 기록은 유지됩니다." }
    private fun setExam(value: ExamRun?) {
        exam.value = value
        saved["exam"] = null
    }
    fun start(selected: Set<String>, types: Set<QuestionType>, count: Int, wrongFirst: Boolean, wrongOnly: Boolean, aiReviewEnabled: Boolean = false) = run {
        val current = repository.snapshot()
        val questions = ExamEngine.select(current, selected, types, count, wrongFirst, wrongOnly)
        require(questions.isNotEmpty()) { "출제할 문제가 없습니다. 카테고리와 문제 유형을 확인해 주세요." }
        val run = ExamRun(items = questions.map { ExamItem(it, CategoryTree.path(current.categories, it.categoryId)) }, aiReviewEnabled = aiReviewEnabled)
        repository.saveDraft(run); setExam(run)
        navigate(Page.EXAM)
    }
    fun submit(input: String) = run {
        val current = exam.value ?: return@run
        if (current.answered) return@run
        val updated = current.submit(input)
        repository.saveDraft(updated); setExam(updated)
        if (updated.pendingReview) reviewRun(updated)
    }
    private suspend fun reviewRun(current: ExamRun) {
        val service = reviewer ?: error("AI 검토가 설정되지 않았습니다.")
        require(current.answered && current.item.question.type == QuestionType.SHORT) { "제출된 단답형 답안만 검토할 수 있습니다." }
        aiReviewing.value = true
        try {
            val result = service.review(current.item.question, current.responses[current.index].input)
            if (exam.value?.id != current.id || exam.value?.index != current.index) return
            val updated = current.withReview(result)
            repository.saveDraft(updated); setExam(updated)
        } finally { aiReviewing.value = false }
    }
    fun reviewAnswer() = run { exam.value?.let { reviewRun(it) } }
    fun acceptLocalGrading() = run { exam.value?.let { val updated = it.acceptLocalGrading(); repository.saveDraft(updated); setExam(updated) } }
    fun toggleCorrect() = run { exam.value?.let { val updated = it.toggle(); repository.saveDraft(updated); setExam(updated) } }
    fun next() = run {
        val current = exam.value ?: return@run
        require(!current.pendingReview) { "AI 검토를 다시 시도하거나 직접 판정해 주세요." }
        if (current.finished) {
            repository.complete(current); navigate(Page.RESULTS)
        } else { val updated = current.next(); repository.saveDraft(updated); setExam(updated) }
    }
    fun abandon() = run { repository.saveDraft(null); setExam(null); navigate(Page.HOME) }
    fun samples() = run { val n = repository.addSamples(); message.value = if (n == 0) "샘플 문제가 이미 있습니다." else "샘플 문제 ${n}개를 별도 카테고리에 추가했습니다." }
    private suspend fun readFile(resolver: ContentResolver, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                require(output.size() + n <= 10 * 1024 * 1024) { "가져올 파일은 10MB 이하여야 합니다." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("파일을 열지 못했습니다.")
        bytes
    }
    fun readCsv(resolver: ContentResolver, uri: Uri) = run {
        csv.value = null
        csv.value = withContext(Dispatchers.Default) { CsvImport.preview(CsvImport.decode(readFile(resolver, uri))) }
    }
    fun importCsv() = run {
        val preview = csv.value ?: return@run
        val count = repository.importCsv(preview)
        csv.value = null; message.value = "문제 ${count}개를 가져왔습니다."; navigate(Page.QUESTIONS)
    }
    fun readBackup(resolver: ContentResolver, uri: Uri) = run {
        backup.value = withContext(Dispatchers.Default) {
            val bytes = readFile(resolver, uri)
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("JSON 백업 파일은 UTF-8 형식이어야 합니다.", e)
            }
            JsonCodec.readBackup(text.removePrefix("\uFEFF"))
        }
    }
    fun restore(mode: RestoreMode) = run {
        val incoming = backup.value ?: return@run
        repository.restore(incoming, mode); backup.value = null; setExam(null)
        message.value = "복원을 완료했습니다."; navigate(Page.HOME)
    }
    fun export(resolver: ContentResolver, uri: Uri) = run {
        val current = repository.snapshot()
        withContext(Dispatchers.IO) {
            val text = JsonCodec.backup(current)
            resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: error("파일을 저장하지 못했습니다.")
        }
        message.value = "카테고리, 문제, 학습 기록을 JSON으로 백업했습니다."
    }
}
