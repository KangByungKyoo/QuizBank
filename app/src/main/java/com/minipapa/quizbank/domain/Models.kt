package com.minipapa.quizbank.domain

import java.util.UUID
import kotlin.random.Random

fun newId(): String = UUID.randomUUID().toString()
enum class QuestionType(val label: String) {
    MULTIPLE("객관식"), OX("O/X형"), SHORT("단답형");
    val enabled get() = this == SHORT
}
data class Category(val id: String = newId(), val name: String, val parentId: String? = null)
data class Question(val id: String = newId(), val categoryId: String, val type: QuestionType,
    val content: String, val options: List<String> = emptyList(), val answer: String,
    val explanation: String = "", val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(), val sample: Boolean = false) {
    val answerText get() = if (type == QuestionType.MULTIPLE) options[answer.toInt()] else answer
    fun validate() {
        require(content.isNotBlank()) { "문제 내용을 입력해 주세요." }
        require(categoryId.isNotBlank()) { "카테고리를 선택해 주세요." }
        when (type) {
            QuestionType.MULTIPLE -> {
                require(options.size in 2..5 && options.all { it.isNotBlank() }) { "객관식 보기는 2~5개이며 빈 보기가 없어야 합니다." }
                require(answer.toIntOrNull() in options.indices && answer == answer.toIntOrNull().toString()) { "정답 보기를 선택해 주세요." }
            }
            QuestionType.OX -> require(answer in listOf("O", "X")) { "O 또는 X를 정답으로 선택해 주세요." }
            QuestionType.SHORT -> require(answer.isNotBlank()) { "단답형 정답을 입력해 주세요." }
        }
    }
    fun correct(input: String) = if (type == QuestionType.SHORT) answer.trim().equals(input.trim(), true) else answer == input
}
data class SessionSummary(val id: String, val startedAt: Long, val completedAt: Long, val total: Int, val correct: Int)
data class AnswerHistory(val id: String, val sessionId: String, val questionId: String?, val categoryId: String?,
    val categoryPath: String, val question: String, val answer: String, val expected: String,
    val explanation: String, val correct: Boolean, val type: QuestionType)
data class Bank(val categories: List<Category> = emptyList(), val questions: List<Question> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(), val answers: List<AnswerHistory> = emptyList()) {
    fun wrongCount(id: String) = answers.count { it.questionId == id && !it.correct }
}
object CategoryTree {
    fun descendants(categories: List<Category>, selected: Set<String>): Set<String> {
        val result = selected.toMutableSet()
        val children = categories.groupBy { it.parentId }
        val queue = ArrayDeque(selected)
        while (queue.isNotEmpty()) children[queue.removeFirst()].orEmpty().forEach {
            if (result.add(it.id)) queue.addLast(it.id)
        }
        return result
    }
    fun path(categories: List<Category>, id: String?): String {
        val byId = categories.associateBy { it.id }
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = id
        while (current != null && visited.add(current)) {
            val category = byId[current] ?: break
            names.add(category.name); current = category.parentId
        }
        return names.asReversed().joinToString("/")
    }
    fun ordered(categories: List<Category>) = categories.sortedBy { path(categories, it.id) }
    fun validate(categories: List<Category>) {
        val byId = categories.associateBy { it.id }
        require(byId.size == categories.size) { "카테고리 ID가 중복되었습니다." }
        require(categories.all { it.id.isNotBlank() && it.name.isNotBlank() && '/' !in it.name }) { "카테고리 이름에 빈 값이나 /를 사용할 수 없습니다." }
        require(categories.map { it.parentId to it.name }.toSet().size == categories.size) { "같은 위치에 같은 이름의 카테고리가 있습니다." }
        categories.forEach { category ->
            val visited = mutableSetOf<String>()
            var current: String? = category.id
            while (current != null) {
                require(visited.add(current)) { "카테고리 부모 관계가 순환합니다." }
                val node = byId[current] ?: error("존재하지 않는 부모 카테고리입니다.")
                current = node.parentId
            }
        }
    }
}
object ExamEngine {
    fun candidates(bank: Bank, selected: Set<String>, types: Set<QuestionType>): List<Question> {
        val ids = CategoryTree.descendants(bank.categories, selected)
        return bank.questions.filter { (selected.isEmpty() || it.categoryId in ids) && it.type.enabled && it.type in types }
    }
    fun select(bank: Bank, selected: Set<String>, types: Set<QuestionType>, count: Int,
        wrongFirst: Boolean, wrongOnly: Boolean = false, random: Random = Random.Default): List<Question> {
        val eligible = candidates(bank, selected, types).filter { !wrongOnly || bank.wrongCount(it.id) > 0 }.shuffled(random)
        val ordered = if (wrongFirst) eligible.filter { bank.wrongCount(it.id) > 0 } + eligible.filter { bank.wrongCount(it.id) == 0 } else eligible
        return if (count == 0) ordered else ordered.take(count)
    }
}
data class ExamItem(val question: Question, val categoryPath: String)
data class ExamResponse(val input: String, val correct: Boolean)
data class ExamRun(val id: String = newId(), val startedAt: Long = System.currentTimeMillis(),
    val items: List<ExamItem>, val index: Int = 0, val responses: List<ExamResponse> = emptyList()) {
    val item get() = items[index]
    val answered get() = responses.size > index
    val finished get() = responses.size == items.size
    val correctCount get() = responses.count { it.correct }
    fun submit(input: String): ExamRun {
        if (answered) return this
        val q = item.question
        require(when (q.type) {
            QuestionType.MULTIPLE -> input.toIntOrNull() in q.options.indices && input == input.toIntOrNull().toString()
            QuestionType.OX -> input in listOf("O", "X")
            QuestionType.SHORT -> input.isNotBlank()
        }) { "답안을 입력하거나 선택해 주세요." }
        return copy(responses = responses + ExamResponse(input, q.correct(input)))
    }
    fun toggle() = if (answered && item.question.type == QuestionType.SHORT)
        copy(responses = responses.toMutableList().also { it[index] = it[index].copy(correct = !it[index].correct) }) else this
    fun next() = if (!answered || index == items.lastIndex) this else copy(index = index + 1)
}
