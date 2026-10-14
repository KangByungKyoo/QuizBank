package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun dateTime(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
@Composable fun StatsScreen(bank: Bank) {
    Body {
        Heading("나의 학습 기록")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("전체 등록 문제 ${bank.questions.size}개")
                Text("완료한 시험 ${bank.sessions.size}회")
                Text("전체 정답률 ${percent(bank.answers.count { it.correct }, bank.answers.size)}%", style = MaterialTheme.typography.headlineMedium)
                Hint("총 ${bank.answers.size}문제 풀이 · 완료한 시험만 집계")
            }
        }
        Heading("카테고리별 통계")
        CategoryTree.ordered(bank.categories).forEach { c ->
            val ids = CategoryTree.descendants(bank.categories, setOf(c.id))
            val history = bank.answers.filter { it.categoryId in ids }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(CategoryTree.path(bank.categories, c.id), fontWeight = FontWeight.Bold)
                    Text("등록 ${bank.questions.count { it.categoryId in ids }}개 · 풀이 ${history.size}회 · 정답률 ${percent(history.count { it.correct }, history.size)}%")
                }
            }
        }
        Hint("상위 카테고리에는 하위 문제와 풀이 기록이 포함됩니다. 삭제된 카테고리의 기록도 전체 정답률에는 남습니다.")
        Heading("시험 이력")
        if (bank.sessions.isEmpty()) Hint("시험을 완료하면 기록이 생깁니다.")
        bank.sessions.sortedByDescending { it.completedAt }.forEach { session ->
            var expanded by remember { mutableStateOf(false) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateTime(session.completedAt), fontWeight = FontWeight.Bold)
                    Text("정답 ${session.correct} / ${session.total} · ${percent(session.correct, session.total)}%")
                    TextButton({ expanded = !expanded }) { Text(if (expanded) "풀이 기록 닫기" else "풀이 기록 보기") }
                    if (expanded) bank.answers.filter { it.sessionId == session.id }.forEach { a ->
                        HorizontalDivider()
                        Hint(a.categoryPath)
                        Text(a.question)
                        Text("내 답: ${a.answer} · ${if (a.correct) "정답" else "오답"}")
                        Text("정답: ${a.expected}")
                        if (a.explanation.isNotEmpty()) Text(a.explanation)
                    }
                }
            }
        }
        Heading("문제별 풀이 횟수")
        bank.questions.forEach { q ->
            val answers = bank.answers.filter { it.questionId == q.id }
            Text(q.content, fontWeight = FontWeight.Bold)
            Hint("풀이 ${answers.size}회 · 오답 ${answers.count { !it.correct }}회")
        }
    }
}
