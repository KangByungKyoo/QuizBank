package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun QuestionsScreen(bank: Bank, model: BankViewModel, wrong: Boolean) {
    var keyword by rememberSaveable(wrong) { mutableStateOf("") }
    var category by rememberSaveable(wrong) { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<Question?>(null) }
    val ids = CategoryTree.descendants(bank.categories, setOfNotNull(category))
    val filtered = bank.questions.filter { (category == null || it.categoryId in ids) &&
        (keyword.isBlank() || it.content.contains(keyword, true) || it.explanation.contains(keyword, true) || it.answerText.contains(keyword, true)) &&
        (!wrong || bank.wrongCount(it.id) > 0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!wrong) Action("＋ 문제 등록", { model.edit() })
                OutlinedTextField(keyword, { keyword = it }, label = { Text("키워드 검색") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                CategoryPicker(bank.categories, category, { category = it }, true)
                if (wrong) {
                    Hint("완료한 시험에서 한 번이라도 틀린 문제입니다. 맞힌 뒤에도 복습 목록에 남습니다.")
                    Action("오답 문제만 다시 풀기", { model.start(setOfNotNull(category), setOf(QuestionType.SHORT), 0, false, true) }, enabled = filtered.any { it.type.enabled })
                }
                Hint("${filtered.size}개 문제 · 하위 카테고리 포함")
                if (filtered.isEmpty()) Text("해당하는 문제가 없습니다.")
            }
        }
        items(filtered, key = { it.id }) { q ->
            var expanded by rememberSaveable(q.id) { mutableStateOf(false) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hint("${CategoryTree.path(bank.categories, q.categoryId)} · ${q.type.label}${if (q.sample) " · 샘플" else ""}")
                    Heading(q.content)
                    val history = bank.answers.filter { it.questionId == q.id }
                    Hint("풀이 ${history.size}회 · 오답 ${history.count { !it.correct }}회")
                    TextButton({ expanded = !expanded }) { Text(if (expanded) "정답과 해설 닫기" else "정답과 해설 보기") }
                    if (expanded) {
                        q.options.forEachIndexed { index, option -> Text("${index + 1}. $option") }
                        Text("정답: ${q.answerText}", color = MaterialTheme.colorScheme.primary)
                        if (q.explanation.isNotEmpty()) Text(q.explanation)
                        Hint("등록 ${dateTime(q.createdAt)} · 수정 ${dateTime(q.updatedAt)}")
                    }
                    Row {
                        TextButton({ model.edit(q) }) { Text("수정") }
                        TextButton({ remove = q }) { Text("삭제") }
                    }
                }
            }
        }
    }
    remove?.let { q -> Confirm("문제를 삭제할까요?", "‘${q.content.take(80)}’ 문제와 보기를 삭제합니다. 이전 시험의 정답률과 풀이 기록은 보존됩니다.",
        { remove = null }, { remove = null; model.deleteQuestion(q.id) }) }
}
