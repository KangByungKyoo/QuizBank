package com.minipapa.quizbank.data

import androidx.room.withTransaction
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.flow.*

class BankRepository(private val db: BankDatabase) {
    private val dao = db.dao()
    val bank: Flow<Bank> = combine(dao.categories(), dao.questions(), dao.options(), dao.sessions(), dao.answers()) {
        _, _, _, _, _ -> Unit
    }.map { snapshot() }
    private suspend fun read(): Bank {
        val options = dao.readOptions().groupBy { it.questionId }
        return Bank(dao.readCategories().map { Category(it.id, it.name, it.parentId) },
            dao.readQuestions().map { Question(it.id, it.categoryId.orEmpty(), QuestionType.valueOf(it.type), it.content,
                options[it.id].orEmpty().sortedBy { o -> o.position }.map { o -> o.text }, it.answer, it.explanation, it.createdAt, it.updatedAt, it.sample) },
            dao.readSessions().map { SessionSummary(it.id, it.startedAt, it.completedAt, it.total, it.correct) },
            dao.readAnswers().map { AnswerHistory(it.id, it.sessionId, it.questionId, it.categoryId, it.categoryPath,
                it.question, it.answer, it.expected, it.explanation, it.correct, QuestionType.valueOf(it.type)) })
    }
    suspend fun snapshot(): Bank = db.withTransaction { read() }
    suspend fun draft(): ExamRun? = JsonCodec.readExam(dao.readDraft())
    suspend fun saveDraft(run: ExamRun?) {
        if (run == null) dao.clearDraft() else dao.putDraft(ExamDraftEntity(json = JsonCodec.exam(run)))
    }
    suspend fun saveCategory(category: Category) = db.withTransaction {
        val current = dao.readCategories().map { Category(it.id, it.name, it.parentId) }
        val updated = category.copy(name = category.name.trim())
        CategoryTree.validate(current.filter { it.id != updated.id } + updated)
        dao.putCategory(CategoryEntity(updated.id, updated.name, updated.parentId))
    }
    suspend fun deleteCategory(id: String) = db.withTransaction {
        val categories = dao.readCategories().map { Category(it.id, it.name, it.parentId) }
        val ids = CategoryTree.descendants(categories, setOf(id))
        // Delete leaves first; avoid SQLite's cascading-trigger depth limit.
        categories.filter { it.id in ids }.sortedByDescending { CategoryTree.path(categories, it.id).length }
            .forEach { dao.deleteCategory(it.id) }
    }
    private suspend fun putQuestion(q: Question) {
        q.validate()
        dao.putQuestion(QuestionEntity(q.id, q.categoryId.takeIf { it.isNotBlank() }, q.type.name, q.content, q.answer, q.explanation, q.createdAt, q.updatedAt, q.sample))
        dao.deleteOptions(q.id)
        dao.putOptions(q.options.mapIndexed { index, text -> QuestionOptionEntity(q.id, index, text) })
    }
    suspend fun saveQuestion(question: Question) = db.withTransaction { putQuestion(question) }
    suspend fun deleteQuestion(id: String) = db.withTransaction { dao.deleteQuestion(id) }
    suspend fun complete(run: ExamRun) = db.withTransaction {
        require(run.finished) { "모든 문제를 푼 뒤 시험을 완료해 주세요." }
        require(!run.aiReviewEnabled || run.responses.all { it.correct || it.review != null || it.manuallyJudged }) {
            "AI 검토를 완료하거나 직접 판정한 뒤 시험을 완료해 주세요."
        }
        val inserted = dao.putSession(ExamSessionEntity(run.id, run.startedAt, System.currentTimeMillis(), run.items.size, run.correctCount))
        if (inserted != -1L) {
            val questions = dao.readQuestions().map { it.id }.toSet()
            val categories = dao.readCategories().map { it.id }.toSet()
            dao.putAnswers(run.items.mapIndexed { index, item ->
                val q = item.question; val response = run.responses[index]
                val input = if (q.type == QuestionType.MULTIPLE) q.options.getOrNull(response.input.toIntOrNull() ?: -1).orEmpty() else response.input
                ExamAnswerEntity("${run.id}:$index", run.id, q.id.takeIf { it in questions }, q.categoryId.takeIf { it in categories },
                    item.categoryPath, q.content, input, q.answerText,
                    listOfNotNull(q.explanation.takeIf { it.isNotBlank() }, response.review?.historyText()?.let {
                        if (response.manuallyJudged) "$it\n최종 판정(직접 변경): ${if (response.correct) "정답" else "오답"}" else it
                    }).joinToString("\n\n"), response.correct, q.type.name)
            })
        }
    }
    suspend fun importCsv(preview: ImportPreview): Int = db.withTransaction {
        val categories = dao.readCategories().map { Category(it.id, it.name, it.parentId) }.toMutableList()
        preview.questions.forEach { imported ->
            var parent: String? = null
            imported.categoryPath.split('/').forEach { name ->
                val found = categories.firstOrNull { it.parentId == parent && it.name == name }
                val category = found ?: Category(name = name, parentId = parent).also {
                    dao.putCategory(CategoryEntity(it.id, it.name, it.parentId)); categories.add(it)
                }
                parent = category.id
            }
            putQuestion(imported.question.copy(categoryId = requireNotNull(parent)))
        }
        preview.questions.size
    }
    suspend fun restore(incoming: Bank, mode: RestoreMode) = db.withTransaction {
        val planned = RestorePlan.build(read(), incoming, mode)
        // A single transaction keeps old data intact if validation or insertion fails.
        dao.clearDraft(); dao.clearSessions(); dao.clearQuestions()
        val oldCategories = dao.readCategories().map { Category(it.id, it.name, it.parentId) }
        oldCategories.sortedByDescending { CategoryTree.path(oldCategories, it.id).length }.forEach { dao.deleteCategory(it.id) }
        val remaining = planned.categories.toMutableList(); val inserted = mutableSetOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { it.parentId == null || it.parentId in inserted }
            require(ready.isNotEmpty()) { "카테고리 구조가 올바르지 않습니다." }
            ready.forEach { dao.putCategory(CategoryEntity(it.id, it.name, it.parentId)); inserted.add(it.id) }
            remaining.removeAll(ready.toSet())
        }
        planned.questions.forEach { putQuestion(it) }
        planned.sessions.forEach { dao.putSession(ExamSessionEntity(it.id, it.startedAt, it.completedAt, it.total, it.correct)) }
        dao.putAnswers(planned.answers.map { ExamAnswerEntity(it.id, it.sessionId, it.questionId, it.categoryId, it.categoryPath,
            it.question, it.answer, it.expected, it.explanation, it.correct, it.type.name) })
    }
    suspend fun addSamples(): Int = db.withTransaction {
        val current = read()
        if (current.questions.any { it.id.startsWith("sample-q-") }) return@withTransaction 0
        val category = current.categories.firstOrNull { it.name == "샘플 문제" && it.parentId == null }
            ?: Category("sample-category", "샘플 문제").also { dao.putCategory(CategoryEntity(it.id, it.name, null)) }
        listOf(
            Question("sample-q-1", category.id, QuestionType.SHORT, "Python에서 리스트의 길이를 구하는 함수는?", answer = "len()", explanation = "len()은 컬렉션의 길이를 반환합니다.", sample = true),
            Question("sample-q-2", category.id, QuestionType.SHORT, "1킬로미터는 몇 미터인가요?", answer = "1000", explanation = "킬로(kilo)는 1,000을 나타냅니다.", sample = true),
            Question("sample-q-3", category.id, QuestionType.SHORT, "대한민국의 수도는?", answer = "서울", explanation = "정답은 서울입니다. 표현이 다르면 직접 정답 처리할 수 있습니다.", sample = true),
            Question("sample-q-4", category.id, QuestionType.SHORT, "4에 더해서 10이 되는 수는?", answer = "6", explanation = "4 + 6 = 10", sample = true),
            Question("sample-q-5", category.id, QuestionType.SHORT, "Hypertext Markup Language의 영문 약자는?", answer = "HTML", explanation = "영문 대소문자와 앞뒤 공백은 구별하지 않습니다.", sample = true)
        ).forEach { putQuestion(it) }
        5
    }
}
