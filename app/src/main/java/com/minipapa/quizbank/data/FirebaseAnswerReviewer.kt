package com.minipapa.quizbank.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableOptions
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseAnswerReviewer : AnswerReviewer {
    override suspend fun review(question: Question, input: String): AnswerReview {
        require(question.type == QuestionType.SHORT) { "단답형만 AI로 검토할 수 있습니다." }
        require(question.content.length <= 4000 && question.answer.length <= 500 &&
            question.explanation.length <= 8000 && input.isNotBlank() && input.length <= 500) {
            "AI 검토는 질문 4,000자, 정답·답안 500자, 해설 8,000자까지 지원합니다."
        }
        try {
            FirebaseApp.getInstance()
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val options = HttpsCallableOptions.Builder().setLimitedUseAppCheckTokens(true).build()
            val result = FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("gradeQuizAnswer", options).call(mapOf(
                    "question" to question.content, "expectedAnswer" to question.answer,
                    "explanation" to question.explanation, "userAnswer" to input)).await()
            return parseAnswerReview(result.data)
        } catch (e: CancellationException) { throw e }
        catch (e: FirebaseFunctionsException) {
            val message = when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED, FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    "AI 검토 인증에 실패했습니다. QuizBank의 App Check 등록과 익명 로그인을 확인해 주세요."
                FirebaseFunctionsException.Code.NOT_FOUND -> "AI 채점 함수가 아직 배포되지 않았습니다."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "AI 검토 사용 한도에 도달했습니다. 잠시 후 또는 다음 날 다시 시도해 주세요."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "질문 또는 답안이 AI 검토에 지원되는 형식·길이를 벗어났습니다."
                else -> "AI 검토에 연결하지 못했습니다. 네트워크와 서버 설정을 확인한 뒤 다시 시도해 주세요."
            }
            throw IllegalStateException(message)
        } catch (e: IllegalArgumentException) { throw e }
        catch (_: Exception) { throw IllegalStateException("AI 검토를 사용할 수 없습니다. Firebase 설정·로그인·네트워크를 확인해 주세요.") }
    }
}

fun parseAnswerReview(data: Any?): AnswerReview {
    val fields = data as? Map<*, *> ?: error("AI 검토 결과를 읽지 못했습니다.")
    fun text(name: String) = fields[name] as? String ?: error("AI 검토 결과에 $name 항목이 없습니다.")
    val verdict = when (text("verdict")) {
        "correct" -> ReviewVerdict.CORRECT
        "partial" -> ReviewVerdict.PARTIAL
        "incorrect" -> ReviewVerdict.INCORRECT
        else -> error("AI 검토의 판정이 올바르지 않습니다.")
    }
    val missing = fields["missingConcepts"] as? List<*> ?: error("AI 검토 결과에 보완 용어가 없습니다.")
    return AnswerReview(verdict, text("feedback"), missing.map { it as? String ?: error("보완 용어 형식이 올바르지 않습니다.") }, text("rationale"))
        .also { it.validate() }
}
