package com.minipapa.quizbank.domain

enum class ReviewVerdict(val label: String) { CORRECT("정답"), PARTIAL("일부 맞음"), INCORRECT("오답") }

data class AnswerReview(val verdict: ReviewVerdict, val feedback: String,
    val missingConcepts: List<String>, val rationale: String) {
    val correct get() = verdict == ReviewVerdict.CORRECT
    fun validate() {
        require(feedback.isNotBlank() && feedback.length <= 2000 && rationale.length <= 2000 &&
            missingConcepts.size <= 10 && missingConcepts.all { it.isNotBlank() && it.length <= 100 }) {
            "AI 검토 결과 형식이 올바르지 않습니다. 다시 시도해 주세요."
        }
        require(verdict != ReviewVerdict.CORRECT || missingConcepts.isEmpty()) { "AI 검토 결과가 서로 모순됩니다. 다시 시도해 주세요." }
        require(verdict != ReviewVerdict.PARTIAL || (missingConcepts.isNotEmpty() && rationale.isNotBlank())) {
            "AI의 보완 설명이 부족합니다. 다시 시도해 주세요."
        }
    }
    fun historyText(): String = buildString {
        append("AI 판정: ${verdict.label}")
        if (missingConcepts.isNotEmpty()) append("\n보완할 용어: ${missingConcepts.joinToString(", ")}")
        append("\n$feedback")
        if (rationale.isNotBlank()) append("\n$rationale")
    }
}

fun interface AnswerReviewer {
    suspend fun review(question: Question, input: String): AnswerReview
}
