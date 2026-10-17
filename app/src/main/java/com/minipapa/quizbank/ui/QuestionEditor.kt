package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*

@Composable fun QuestionEditor(bank: Bank, existing: Question?, onSave: (Question) -> Unit, onCancel: () -> Unit) {
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId?.takeIf { it.isNotBlank() }) }
    var typeName by rememberSaveable(existing?.id) { mutableStateOf((existing?.type ?: QuestionType.SHORT).name) }
    val type = QuestionType.valueOf(typeName)
    var content by rememberSaveable(existing?.id) { mutableStateOf(existing?.content.orEmpty()) }
    var options by rememberSaveable(existing?.id) { mutableStateOf(existing?.options?.takeIf { it.isNotEmpty() } ?: listOf("", "")) }
    var correctIndex by rememberSaveable(existing?.id) { mutableIntStateOf(if (existing?.type == QuestionType.MULTIPLE) existing.answer.toInt() else 0) }
    var short by rememberSaveable(existing?.id) { mutableStateOf(existing?.answerText.orEmpty()) }
    var ox by rememberSaveable(existing?.id) { mutableStateOf(if (existing?.type == QuestionType.OX) existing.answer else "O") }
    var explanation by rememberSaveable(existing?.id) { mutableStateOf(existing?.explanation.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Body {
        Hint("정답만 입력해도 저장할 수 있어요. 카테고리와 문제 내용까지 채우면 Ready로 바뀌어 시험에 출제됩니다. 해설은 선택입니다.")
        if (bank.categories.isEmpty()) Hint("카테고리는 나중에 문제 탭의 카테고리 관리에서 추가할 수 있어요.")
        CategoryPicker(bank.categories, category, { category = it })
        if (category != null) TextButton({ category = null }) { Text("카테고리 선택 해제") }
        Heading("문제 유형")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionType.entries.forEach { item -> FilterChip(type == item, { typeName = item.name }, label = { Text(item.label) }, enabled = item.enabled) }
        }
        Hint("단답형만 등록·출제할 수 있습니다. 등록한 문제 내용과 정답을 그대로 사용합니다.")
        if (!type.enabled) Hint("기존 유형의 문제입니다. 단답형을 선택하면 등록된 정답을 가져옵니다.")
        OutlinedTextField(content, { content = it }, label = { Text("문제 내용") }, minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("question-content"))
        when (type) {
            QuestionType.MULTIPLE -> {
                Heading("보기와 정답")
                Hint("정답 보기의 원을 선택하세요. 출제 시 보기 순서는 유지됩니다.")
                options.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(correctIndex == index, { correctIndex = index })
                        OutlinedTextField(value, { changed -> options = options.toMutableList().also { it[index] = changed } },
                            label = { Text("보기 ${index + 1}") }, modifier = Modifier.weight(1f).testTag("option-$index"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ options = options + "" }, enabled = options.size < 5) { Text("보기 추가") }
                    OutlinedButton({ options = options.dropLast(1); if (correctIndex >= options.size) correctIndex = 0 }, enabled = options.size > 2) { Text("마지막 보기 삭제") }
                }
            }
            QuestionType.OX -> Row {
                listOf("O", "X").forEach { value -> FilterChip(ox == value, { ox = value }, label = { Text(value) }, modifier = Modifier.padding(end = 12.dp)) }
            }
            QuestionType.SHORT -> {
                OutlinedTextField(short, { short = it }, label = { Text("정답") }, modifier = Modifier.fillMaxWidth().testTag("short-answer"))
                Hint("앞뒤 공백과 영문 대소문자를 구별하지 않습니다. 풀이 후 정답 여부를 직접 바꿀 수 있습니다.")
            }
        }
        OutlinedTextField(explanation, { explanation = it }, label = { Text("해설 (선택)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Action("문제 저장", {
            val q = Question(existing?.id ?: newId(), category.orEmpty(), type, content,
                if (type == QuestionType.MULTIPLE) options else emptyList(),
                when (type) { QuestionType.MULTIPLE -> "$correctIndex"; QuestionType.OX -> ox; QuestionType.SHORT -> short },
                explanation, existing?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis(), existing?.sample ?: false)
            runCatching { q.validate(); require(q.categoryId.isBlank() || bank.categories.any { it.id == q.categoryId }) { "카테고리를 다시 선택해 주세요." } }
                .onSuccess { onSave(q) }.onFailure { error = it.message }
        }, modifier = Modifier.testTag("save-question"), enabled = type.enabled)
        TextButton(onCancel, Modifier.fillMaxWidth()) { Text("취소") }
    }
}
