package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.res.painterResource
import com.minipapa.quizbank.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (wrong) 20.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (wrong) 16.dp else 12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wrong) {
                    KeywordSearchField(keyword, { keyword = it })
                    CategoryPicker(bank.categories, category, { category = it }, true)
                } else BoxWithConstraints {
                    val stackFilters = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
                    if (stackFilters) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeywordSearchField(keyword, { keyword = it })
                        CategoryPicker(bank.categories, category, { category = it }, true,
                            minHeight = 48.dp, compact = true)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KeywordSearchField(keyword, { keyword = it }, Modifier.weight(1.35f))
                        CategoryPicker(bank.categories, category, { category = it }, true,
                            minHeight = 48.dp, modifier = Modifier.weight(1f), compact = true)
                    }
                }
                if (!wrong) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("전체", "Not Ready", "Ready").forEach { item ->
                        FilterChip(status == item, { status = item }, label = { Text(item) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("status-filter-$item"))
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
            if (!wrong) {
                ManagementQuestionCard(bank, q, onEdit = { model.edit(q) }, onDelete = { remove = q })
                return@items
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("\uD0A4\uC6CC\uB4DC \uAC80\uC0C9") },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(enabled = true, isError = false,
                        interactionSource = interactionSource, shape = RoundedCornerShape(10.dp))
                }
            )
        }
    )
}

@Composable
private fun ManagementQuestionCard(bank: Bank, q: Question, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable(q.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val history = bank.answers.filter { it.questionId == q.id }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                  Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("${CategoryTree.path(bank.categories, q.categoryId).ifBlank { "카테고리 없음" }} · ${q.type.label}${if (q.sample) " · 샘플" else ""}",
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                  }
                }
                Box {
                    IconButton({ menuOpen = true }, enabled = !LocalBusy.current) {
                        Icon(Icons.Default.MoreVert, contentDescription = "문제 작업 메뉴", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("수정") }, onClick = { menuOpen = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) })
                        DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) })
                    }
                }
            }
            Text(if (q.content.isBlank()) "문제 내용 미입력" else managementContent(q.content),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (!q.isReady) {
                Text("Not Ready", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Hint("보완 필요: ${q.missingRequiredFields.joinToString(" · ")}")
            }
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Text("정답: ${q.answerText}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text("풀이 ${history.size}회 · 오답 ${history.count { !it.correct }}회",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_book_open), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "해설 닫기" else "해설 보기")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VerticalDivider(Modifier.height(20.dp))
                    TextButton(onEdit, enabled = !LocalBusy.current, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("수정", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VerticalDivider(Modifier.height(20.dp))
                    TextButton(onDelete, enabled = !LocalBusy.current, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("삭제", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEachIndexed { index, option -> Text("${index + 1}. $option") }
                Text(q.explanation.ifBlank { "등록된 해설이 없습니다." }, style = MaterialTheme.typography.bodyMedium)
                Hint("등록 ${dateTime(q.createdAt)} · 수정 ${dateTime(q.updatedAt)}")
            }
        }
    }
}
