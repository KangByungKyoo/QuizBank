package com.minipapa.quizbank.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun HomeScreen(bank: Bank, pending: ExamRun?, model: BankViewModel) {
    val selected by model.homeCategory.collectAsStateWithLifecycle()
    val category = selected?.takeIf { id -> bank.categories.any { it.id == id } }
    val available = ExamEngine.candidates(bank, setOfNotNull(category), setOf(QuestionType.SHORT))
    Body {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeStat("등록 문제", "${bank.questions.size}개", Modifier.weight(1f))
            HomeStat("완료한 시험", "${bank.sessions.size}회", Modifier.weight(1f))
        }
        Text("시험 카테고리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category == null, { model.selectHomeCategory(null) }, label = { Text("전체") })
            CategoryTree.ordered(bank.categories).forEach { c ->
                FilterChip(category == c.id, { model.selectHomeCategory(c.id) }, label = { Text(CategoryTree.path(bank.categories, c.id)) })
            }
        }
        Action("랜덤 시험 시작", { model.navigate(Page.EXAM_SETUP) }, Modifier.heightIn(min = 72.dp), enabled = available.isNotEmpty())
        if (available.isEmpty()) Hint(if (bank.questions.isEmpty()) "문제를 추가하면 시험을 시작할 수 있어요." else "출제 가능한 Ready 단답형 문제가 없습니다. 문제 관리에서 카테고리와 문제 내용을 채워 주세요.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ model.navigate(Page.WRONG) }, Modifier.weight(1f).heightIn(min = 56.dp), enabled = !LocalBusy.current) { Text("오답 복습") }
            OutlinedButton({ model.edit() }, Modifier.weight(1f).heightIn(min = 56.dp), enabled = !LocalBusy.current) { Text("문제 추가") }
        }
        if (pending != null) OutlinedButton({ model.navigate(Page.EXAM) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !LocalBusy.current) {
            Text("진행 중인 시험 이어하기 (${pending.index + 1}/${pending.items.size})")
        }
    }
}

@Composable private fun HomeStat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Hint(label)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun MenuScreen(learning: Boolean, model: BankViewModel) {
    Body {
        if (learning) {
            MenuItem("오답 노트", "틀렸던 문제 다시 확인하기", { model.navigate(Page.WRONG) })
            MenuItem("학습 통계", "시험 이력과 정답률", { model.navigate(Page.STATS) })
        } else {
            MenuItem("문제 등록", "새 문제와 정답 추가", { model.edit() })
            MenuItem("문제 관리", "검색 · 정답 확인 · 수정 · 삭제", { model.navigate(Page.QUESTIONS) })
            MenuItem("문제 출력", "A4 문제지 · 정답 및 해설 · PDF 저장", { model.navigate(Page.PRINT) })
            MenuItem("카테고리 관리", "분류와 하위 카테고리 관리", { model.navigate(Page.CATEGORIES) })
        }
    }
}

@Composable private fun MenuItem(title: String, description: String, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth(), enabled = !LocalBusy.current) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Hint(description)
        }
    }
}
