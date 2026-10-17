package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun QuestionsScreen(bank: Bank, model: BankViewModel, wrong: Boolean) {
    var keyword by rememberSaveable(wrong) { mutableStateOf("") }
    var category by rememberSaveable(wrong) { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<Question?>(null) }
    var status by rememberSaveable(wrong) { mutableStateOf("전체") }
    val ids = CategoryTree.descendants(bank.categories, setOfNotNull(category))
    val filtered = bank.questions.filter { (category == null || it.categoryId in ids) &&
        (keyword.isBlank() || it.content.contains(keyword, true) || it.explanation.contains(keyword, true) || it.answerText.contains(keyword, true)) &&
        (wrong || status == "전체" || (status == "Ready") == it.isReady) &&
        (!wrong || bank.wrongCount(it.id) > 0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!wrong) Action("＋ 문제 등록", { model.edit() })
                OutlinedTextField(keyword, { keyword = it }, label = { Text("키워드 검색") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                CategoryPicker(bank.categories, category, { category = it }, true)
                if (!wrong) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("전체", "Not Ready", "Ready").forEach { item ->
                        FilterChip(status == item, { status = item }, label = { Text(item) }, modifier = Modifier.testTag("status-filter-$item"))
                    }
                }
                if (wrong) {
                    Hint("완료한 시험에서 한 번이라도 틀린 문제입니다. 맞힌 뒤에도 복습 목록에 남습니다.")
                    Action("오답 문제만 다시 풀기", { model.start(setOfNotNull(category), setOf(QuestionType.SHORT), 0, false, true) }, enabled = filtered.any { it.type.enabled && it.isReady })
                }
                Hint("${filtered.size}개 문제 · 하위 카테고리 포함")
                if (filtered.isEmpty()) Text("해당하는 문제가 없습니다.")
            }
        }
        items(filtered, key = { it.id }) { q ->
            var expanded by rememberSaveable(q.id) { mutableStateOf(false) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = if (wrong) 18.dp else 16.dp, top = if (wrong) 18.dp else 16.dp,
                    end = if (wrong) 18.dp else 16.dp, bottom = if (wrong) 18.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (wrong) 10.dp else 8.dp)) {
                    Hint("${CategoryTree.path(bank.categories, q.categoryId).ifBlank { "카테고리 없음" }} · ${q.type.label}${if (q.sample) " · 샘플" else ""}")
                    if (!q.isReady) {
                        Text("Not Ready", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Hint("보완 필요: ${q.missingRequiredFields.joinToString(" · ")}")
                    }
                    if (wrong) Heading(q.content.ifBlank { q.answerText })
                    else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (q.content.isBlank()) "문제 내용 미입력" else managementContent(q.content),
                            style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (q.content.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("정답: ${q.answerText}", style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                    val history = bank.answers.filter { it.questionId == q.id }
                    Text("풀이 ${history.size}회 · 오답 ${history.count { !it.correct }}회",
                        style = if (wrong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!wrong) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton({ expanded = !expanded }, contentPadding = PaddingValues(8.dp)) {
                                Text(if (expanded) "해설 닫기" else "해설 보기")
                            }
                            Row {
                                TextButton({ model.edit(q) }, contentPadding = PaddingValues(8.dp)) { Text("수정") }
                                TextButton({ remove = q }, contentPadding = PaddingValues(8.dp)) { Text("삭제") }
                            }
                        }
                        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            q.options.forEachIndexed { index, option -> Text("${index + 1}. $option") }
                            Text(q.explanation.ifBlank { "등록된 해설이 없습니다." }, style = MaterialTheme.typography.bodyMedium)
                            Hint("등록 ${dateTime(q.createdAt)} · 수정 ${dateTime(q.updatedAt)}")
                        }
                    } else CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
                        val buttonModifier = if (wrong) Modifier else Modifier.height(32.dp)
                        val buttonPadding = if (wrong) ButtonDefaults.TextButtonContentPadding else PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(if (wrong) 10.dp else 4.dp)) {
                            TextButton({ expanded = !expanded }, modifier = buttonModifier, contentPadding = buttonPadding) {
                                Text(if (expanded) "정답과 해설 닫기" else "정답과 해설 보기")
                            }
                            if (expanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    q.options.forEachIndexed { index, option -> Text("${index + 1}. $option") }
                                    Text("정답: ${q.answerText}", color = MaterialTheme.colorScheme.primary)
                                    if (q.explanation.isNotEmpty()) Text(q.explanation)
                                    Hint("등록 ${dateTime(q.createdAt)} · 수정 ${dateTime(q.updatedAt)}")
                                }
                            }
                            Row {
                                TextButton({ model.edit(q) }, modifier = buttonModifier, contentPadding = buttonPadding) { Text("수정") }
                                TextButton({ remove = q }, modifier = buttonModifier, contentPadding = buttonPadding) { Text("삭제") }
                            }
                        }
                    }
                }
            }
        }
    }
    remove?.let { q -> Confirm("문제를 삭제할까요?", "‘${(if (q.content.isBlank()) q.answerText else if (wrong) q.content else managementContent(q.content)).take(80)}’ 문제와 보기를 삭제합니다. 이전 시험의 정답률과 풀이 기록은 보존됩니다.",
        { remove = null }, { remove = null; model.deleteQuestion(q.id) }) }
}

private fun managementContent(content: String): String {
    val prompt = "다음 설명에 해당하는 용어는 무엇인가요?"
    val trimmed = content.trimStart()
    if (!trimmed.startsWith(prompt)) return content
    return trimmed.removePrefix(prompt).trimStart().ifBlank { content }
}
