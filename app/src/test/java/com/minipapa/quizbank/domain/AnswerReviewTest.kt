package com.minipapa.quizbank.domain

import com.minipapa.quizbank.data.parseAnswerReview
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnswerReviewTest {
    private val q = Question("docker", "work", QuestionType.SHORT,
        "컨테이너 실행에 필요한 파일과 설정의 묶음은?", answer = "Docker 이미지", explanation = "읽기 전용 템플릿입니다.")
    private val partial = AnswerReview(ReviewVerdict.PARTIAL, "Docker는 관련 있지만 이미지가 빠졌습니다.",
        listOf("이미지"), "이미지는 컨테이너 생성에 사용하는 읽기 전용 템플릿입니다.")
    @Test fun partialFeedbackDoesNotAwardFullCreditAndSurvivesRestartAndManualOverride() {
        val original = ExamRun(items = listOf(ExamItem(q, "회사 업무")), aiReviewEnabled = true).submit("도커")
        assertTrue(original.pendingReview)
        val reviewed = original.withReview(partial)
        assertFalse(reviewed.pendingReview)
        assertEquals(0, reviewed.correctCount)
        assertEquals(listOf("이미지"), reviewed.responses.single().review!!.missingConcepts)
        assertEquals(reviewed, JsonCodec.readExam(JsonCodec.exam(reviewed)))
        val manual = reviewed.toggle()
        assertEquals(1, manual.correctCount)
        assertTrue(manual.responses.single().manuallyJudged)
        assertEquals(partial, manual.responses.single().review)
        assertEquals(manual, JsonCodec.readExam(JsonCodec.exam(manual)))
    }
    @Test fun equivalentKoreanAnswerCanBeAcceptedByValidatedReview() {
        val run = ExamRun(items = listOf(ExamItem(q, "회사 업무"))).submit("도커 이미지")
            .withReview(AnswerReview(ReviewVerdict.CORRECT, "같은 개념의 한국어 표기입니다.", emptyList(), ""))
        assertEquals(1, run.correctCount)
        assertEquals("도커 이미지", run.responses.single().input)
    }
    @Test fun failedAiReviewMustBeResolvedBeforeAdvancingButExactAnswersNeedNoReview() {
        val run = ExamRun(items = listOf(ExamItem(q, "회사 업무"), ExamItem(q.copy(id = "second"), "회사 업무")), aiReviewEnabled = true)
        val pending = run.submit("도커")
        assertTrue(pending.pendingReview)
        assertEquals(pending, pending.next())
        assertFalse(pending.acceptLocalGrading().pendingReview)
        assertEquals(1, pending.acceptLocalGrading().next().index)
        assertFalse(run.submit(" Docker 이미지 ").pendingReview)
    }
    @Test fun legacyDraftLoadsWithoutAiFields() {
        val run = ExamRun(items = listOf(ExamItem(q, "회사 업무"))).submit("Docker 이미지")
        val json = JSONObject(JsonCodec.exam(run)).also {
            it.remove("aiReviewEnabled")
            it.getJSONArray("responses").getJSONObject(0).also { response -> response.remove("review"); response.remove("manuallyJudged") }
        }
        assertEquals(run, JsonCodec.readExam(json.toString()))
    }
    @Test fun malformedAndContradictoryServerResultsAreRejected() {
        val valid = mapOf("verdict" to "partial", "feedback" to partial.feedback,
            "missingConcepts" to partial.missingConcepts, "rationale" to partial.rationale)
        assertEquals(partial, parseAnswerReview(valid))
        listOf(null, valid + ("verdict" to "unknown"), valid + ("feedback" to ""),
            valid + ("missingConcepts" to listOf(12)), valid + ("rationale" to ""),
            valid + ("verdict" to "correct")).forEach {
            assertTrue(runCatching { parseAnswerReview(it) }.isFailure)
        }
    }
}
