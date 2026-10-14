package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun HomeScreen(bank: Bank, pending: ExamRun?, model: BankViewModel) {
    var samples by remember { mutableStateOf(false) }
    Body {
        Hint("QUIZBANK · 나만의 문제 은행")
        Heading("쌓아 둔 문제로,\n오늘도 한 번 더 배워요.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("등록 문제"); Text("${bank.questions.size}개", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                Column { Text("완료한 시험"); Text("${bank.sessions.size}회", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            }
        }
        if (pending != null) Action("진행 중인 시험 이어하기 (${pending.index + 1}/${pending.items.size})", { model.navigate(Page.EXAM) })
        Action("랜덤 시험 시작", { model.navigate(Page.EXAM_SETUP) }, enabled = bank.questions.any { it.type.enabled })
        Tile("＋ 문제 등록", "단답형 문제와 정답 등록", { model.edit() })
        Tile("문제 관리", "문제 검색, 정답 확인, 수정과 삭제", { model.navigate(Page.QUESTIONS) })
        Tile("카테고리 관리", "분야와 주제별로 차곡차곡", { model.navigate(Page.CATEGORIES) })
        Tile("오답 노트", "틀렸던 문제 ${bank.questions.count { bank.wrongCount(it.id) > 0 }}개 복습", { model.navigate(Page.WRONG) })
        Tile("학습 통계", "시험 이력과 정답률 확인", { model.navigate(Page.STATS) })
        Tile("데이터 관리", "CSV 가져오기 · JSON 백업과 복원", { model.navigate(Page.SETTINGS) })
        Heading("카테고리별 문제")
        if (bank.categories.isEmpty()) Hint("아직 카테고리가 없어요. 카테고리를 만들거나 샘플 문제로 시작해 보세요.")
        CategoryTree.ordered(bank.categories).forEach { category ->
            val ids = CategoryTree.descendants(bank.categories, setOf(category.id))
            Text("${CategoryTree.path(bank.categories, category.id)} · ${bank.questions.count { it.categoryId in ids }}개")
        }
        Hint("상위 카테고리의 문제 수는 모든 하위 카테고리를 포함합니다.")
        OutlinedButton({ samples = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("샘플 문제 5개 추가") }
        Hint("샘플은 별도 카테고리와 ‘샘플’ 표시로 구분됩니다. 모든 데이터는 이 기기에 저장됩니다.")
    }
    if (samples) Confirm("샘플 문제를 추가할까요?", "‘샘플 문제’ 카테고리에 단답형 문제 5개를 추가합니다.",
        { samples = false }, { samples = false; model.samples() })
}
