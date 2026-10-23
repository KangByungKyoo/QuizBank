package com.minipapa.quizbank.domain

import kotlin.random.Random

data class PrintOptions(
    val categoryIds: Set<String> = emptySet(),
    val includeDescendants: Boolean = true,
    val types: Set<QuestionType> = QuestionType.entries.toSet(),
    val count: Int = 20,
    val randomOrder: Boolean = false,
    val includeAnswers: Boolean = true,
    val includeExplanations: Boolean = true,
    val answerLines: Int = 1
)

/** Immutable selection shared by both PDFs. Never writes questions or learning history. */
data class PrintSelection(val questions: List<Question>, val subtitle: String, val options: PrintOptions) {
    val typeLabel get() = questions.map { it.type }.distinct().joinToString(" · ") { it.label }
}

object PrintSelector {
    fun candidates(bank: Bank, options: PrintOptions): List<Question> {
        val ids = if (options.includeDescendants) CategoryTree.descendants(bank.categories, options.categoryIds) else options.categoryIds
        // Printing supports all stored types, including legacy multiple-choice and O/X questions.
        return bank.questions.distinctBy { it.id }.filter { q ->
            q.isReady && q.type in options.types &&
                (options.categoryIds.isEmpty() || q.categoryId in ids) && runCatching { q.validate() }.isSuccess
        }
    }

    fun select(bank: Bank, options: PrintOptions, random: Random = Random.Default): PrintSelection {
        require(options.count in setOf(0, 10, 20, 50)) { "문제 수를 확인해 주세요." }
        require(options.answerLines in 1..3) { "답안 공간은 1~3줄이어야 합니다." }
        val candidates = candidates(bank, options).sortedWith(compareBy<Question> { it.createdAt }.thenBy { it.id })
        val ordered = if (options.randomOrder) candidates.shuffled(random) else candidates
        val selected = (if (options.count == 0) ordered else ordered.take(options.count)).map { it.copy(options = it.options.toList()) }
        require(selected.isNotEmpty()) { "출력할 문제가 없습니다. 카테고리와 문제 유형을 확인해 주세요." }
        val paths = bank.categories.filter { it.id in options.categoryIds }.map { CategoryTree.path(bank.categories, it.id) }.sorted()
        val subtitle = when {
            options.categoryIds.isEmpty() -> "전체 카테고리"
            paths.isEmpty() -> "선택한 카테고리"
            paths.size == 1 -> paths.single()
            else -> "${paths.first()} 외 ${paths.size - 1}개 카테고리"
        }
        return PrintSelection(selected, subtitle, options.copy(categoryIds = options.categoryIds.toSet(), types = options.types.toSet()))
    }
}
