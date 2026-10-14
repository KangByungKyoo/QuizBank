package com.minipapa.quizbank.domain

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.nullString(key: String): String? = if (isNull(key)) null else getString(key)
private fun <T> JSONArray.items(read: (JSONObject) -> T) = List(length()) { read(getJSONObject(it)) }
object JsonCodec {
    private fun question(q: Question) = JSONObject().put("id", q.id).put("categoryId", q.categoryId)
        .put("type", q.type.name).put("content", q.content).put("options", JSONArray(q.options)).put("answer", q.answer)
        .put("explanation", q.explanation).put("createdAt", q.createdAt).put("updatedAt", q.updatedAt).put("sample", q.sample)
    private fun readQuestion(o: JSONObject): Question {
        val options = o.getJSONArray("options")
        return Question(o.getString("id"), o.getString("categoryId"), QuestionType.valueOf(o.getString("type")),
            o.getString("content"), List(options.length()) { options.getString(it) }, o.getString("answer"),
            o.getString("explanation"), o.getLong("createdAt"), o.getLong("updatedAt"), o.optBoolean("sample", false))
    }
    fun backup(bank: Bank): String = JSONObject().put("format", "QuizBank").put("version", 1)
        .put("categories", JSONArray(bank.categories.map { JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: JSONObject.NULL) }))
        .put("questions", JSONArray(bank.questions.map(::question)))
        .put("sessions", JSONArray(bank.sessions.map { JSONObject().put("id", it.id).put("startedAt", it.startedAt)
            .put("completedAt", it.completedAt).put("total", it.total).put("correct", it.correct) }))
        .put("answers", JSONArray(bank.answers.map { JSONObject().put("id", it.id).put("sessionId", it.sessionId)
            .put("questionId", it.questionId ?: JSONObject.NULL).put("categoryId", it.categoryId ?: JSONObject.NULL)
            .put("categoryPath", it.categoryPath).put("question", it.question).put("answer", it.answer)
            .put("expected", it.expected).put("explanation", it.explanation).put("correct", it.correct).put("type", it.type.name) })).toString(2)
    fun readBackup(text: String): Bank {
        val root = JSONObject(text)
        require(root.getString("format") == "QuizBank" && root.getInt("version") == 1) { "지원하지 않는 QuizBank 백업 형식 또는 버전입니다." }
        val bank = Bank(root.getJSONArray("categories").items { Category(it.getString("id"), it.getString("name"), it.nullString("parentId")) },
            root.getJSONArray("questions").items(::readQuestion),
            root.optJSONArray("sessions")?.items { SessionSummary(it.getString("id"), it.getLong("startedAt"), it.getLong("completedAt"), it.getInt("total"), it.getInt("correct")) }.orEmpty(),
            root.optJSONArray("answers")?.items { AnswerHistory(it.getString("id"), it.getString("sessionId"), it.nullString("questionId"),
                it.nullString("categoryId"), it.getString("categoryPath"), it.getString("question"), it.getString("answer"),
                it.getString("expected"), it.getString("explanation"), it.getBoolean("correct"), QuestionType.valueOf(it.getString("type"))) }.orEmpty())
        validate(bank)
        return bank
    }
    fun validate(bank: Bank) {
        CategoryTree.validate(bank.categories)
        val cats = bank.categories.map { it.id }.toSet(); val qs = bank.questions.map { it.id }.toSet()
        require(qs.size == bank.questions.size && bank.questions.all { it.id.isNotBlank() && it.categoryId in cats }) { "문제 ID 중복 또는 잘못된 카테고리입니다." }
        bank.questions.forEach { it.validate() }
        val sessions = bank.sessions.associateBy { it.id }
        require(sessions.size == bank.sessions.size && sessions.keys.all { it.isNotBlank() }) { "시험 ID가 중복되거나 비어 있습니다." }
        require(bank.answers.map { it.id }.toSet().size == bank.answers.size) { "풀이 기록 ID가 중복되었습니다." }
        bank.answers.forEach {
            require(it.id.isNotBlank() && it.sessionId in sessions && (it.questionId == null || it.questionId in qs) &&
                (it.categoryId == null || it.categoryId in cats)) { "풀이 기록의 참조가 올바르지 않습니다." }
        }
        val groups = bank.answers.groupBy { it.sessionId }
        bank.sessions.forEach {
            val answers = groups[it.id].orEmpty()
            require(it.total > 0 && answers.size == it.total && answers.count { a -> a.correct } == it.correct) { "시험 결과와 풀이 기록이 일치하지 않습니다." }
        }
    }
    fun exam(run: ExamRun): String = JSONObject().put("id", run.id).put("startedAt", run.startedAt).put("index", run.index)
        .put("aiReviewEnabled", run.aiReviewEnabled)
        .put("items", JSONArray(run.items.map { JSONObject().put("question", question(it.question)).put("path", it.categoryPath) }))
        .put("responses", JSONArray(run.responses.map { response -> JSONObject().put("input", response.input).put("correct", response.correct).put("manuallyJudged", response.manuallyJudged)
            .put("review", response.review?.let { JSONObject().put("verdict", it.verdict.name).put("feedback", it.feedback)
                .put("missingConcepts", JSONArray(it.missingConcepts)).put("rationale", it.rationale) } ?: JSONObject.NULL) })).toString()
    fun readExam(text: String?): ExamRun? = runCatching {
        if (text == null) return null
        val o = JSONObject(text)
        val run = ExamRun(o.getString("id"), o.getLong("startedAt"), o.getJSONArray("items").items {
            ExamItem(readQuestion(it.getJSONObject("question")), it.getString("path")) }, o.getInt("index"),
            o.getJSONArray("responses").items { response ->
                val review = response.optJSONObject("review")?.let {
                    val missing = it.getJSONArray("missingConcepts")
                    AnswerReview(ReviewVerdict.valueOf(it.getString("verdict")), it.getString("feedback"),
                        List(missing.length()) { index -> missing.getString(index) }, it.getString("rationale")).also { it.validate() }
                }
                ExamResponse(response.getString("input"), response.getBoolean("correct"), review, response.optBoolean("manuallyJudged", false))
            }, o.optBoolean("aiReviewEnabled", false))
        require(run.items.isNotEmpty() && run.index in run.items.indices && run.responses.size in run.index..run.index + 1)
        run
    }.getOrNull()
}
enum class RestoreMode(val label: String) { MERGE("중복 문제 건너뛰고 병합"), OVERWRITE("같은 ID 문제 덮어쓰기"), REPLACE("전체 데이터 교체") }
object RestorePlan {
    fun build(current: Bank, incoming: Bank, mode: RestoreMode): Bank {
        JsonCodec.validate(incoming)
        if (mode == RestoreMode.REPLACE) return incoming
        val categories = current.categories.associateBy { it.id }.toMutableMap()
        val mapping = mutableMapOf<String, String>()
        val remaining = incoming.categories.toMutableList()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { it.parentId == null || it.parentId in mapping }
            require(ready.isNotEmpty()) { "카테고리 구조를 복원할 수 없습니다." }
            ready.forEach { c ->
                val parent = c.parentId?.let { mapping.getValue(it) }
                val sameId = categories[c.id]
                val samePath = categories.values.firstOrNull { it.parentId == parent && it.name == c.name }
                val id = sameId?.id ?: samePath?.id ?: c.id
                mapping[c.id] = id
                if (sameId == null && samePath == null) categories[id] = c.copy(parentId = parent)
                else if (mode == RestoreMode.OVERWRITE && sameId != null) categories[id] = c.copy(parentId = parent)
            }
            remaining.removeAll(ready.toSet())
        }
        val questions = current.questions.associateBy { it.id }.toMutableMap()
        incoming.questions.forEach { q ->
            if (q.id !in questions || mode == RestoreMode.OVERWRITE) questions[q.id] = q.copy(categoryId = mapping.getValue(q.categoryId))
        }
        val sessions = current.sessions.toMutableList()
        val answers = current.answers.toMutableList()
        val known = sessions.map { it.id }.toSet()
        incoming.sessions.filter { it.id !in known }.forEach { s ->
            sessions.add(s)
            answers.addAll(incoming.answers.filter { it.sessionId == s.id }.map {
                it.copy(categoryId = it.categoryId?.let(mapping::getValue))
            })
        }
        return Bank(categories.values.toList(), questions.values.toList(), sessions, answers).also(JsonCodec::validate)
    }
}
