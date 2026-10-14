package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.BankViewModel

@Composable fun CategoriesScreen(bank: Bank, model: BankViewModel) {
    var form by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var remove by remember { mutableStateOf<Category?>(null) }
    Body {
        Hint("상위 카테고리를 선택하면 하위 문제도 함께 검색하고 출제합니다.")
        Action("＋ 카테고리 추가", { edit = null; form = true })
        if (bank.categories.isEmpty()) Hint("아직 카테고리가 없습니다.")
        CategoryTree.ordered(bank.categories).forEach { c ->
            val descendants = CategoryTree.descendants(bank.categories, setOf(c.id))
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heading(CategoryTree.path(bank.categories, c.id))
                    Hint("하위 포함 문제 ${bank.questions.count { it.categoryId in descendants }}개")
                    Row {
                        TextButton({ edit = c; form = true }) { Text("수정") }
                        TextButton({ edit = Category(name = "", parentId = c.id); form = true }) { Text("하위 추가") }
                        TextButton({ remove = c }) { Text("삭제") }
                    }
                }
            }
        }
    }
    if (form) {
        var name by remember(edit) { mutableStateOf(edit?.name.orEmpty()) }
        var parent by remember(edit) { mutableStateOf(edit?.parentId) }
        var error by remember { mutableStateOf<String?>(null) }
        val excluded = edit?.let { CategoryTree.descendants(bank.categories, setOf(it.id)) }.orEmpty()
        AlertDialog(onDismissRequest = { form = false }, title = { Text("카테고리 ${if (edit?.id in bank.categories.map { it.id }) "수정" else "추가"}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                CategoryPicker(bank.categories.filter { it.id !in excluded }, parent, { parent = it }, true, "부모 카테고리")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton({
                val candidate = Category(edit?.id ?: newId(), name.trim(), parent)
                runCatching { CategoryTree.validate(bank.categories.filter { it.id != candidate.id } + candidate) }
                    .onSuccess { model.saveCategory(candidate); form = false }.onFailure { error = it.message }
            }, enabled = !LocalBusy.current) { Text("저장") } }, dismissButton = { TextButton({ form = false }) { Text("취소") } })
    }
    remove?.let { c ->
        val ids = CategoryTree.descendants(bank.categories, setOf(c.id))
        Confirm("카테고리를 삭제할까요?", "‘${c.name}’와 하위 카테고리 ${ids.size - 1}개, 포함된 문제 ${bank.questions.count { it.categoryId in ids }}개가 삭제됩니다. 이전 시험 기록은 보존됩니다.",
            { remove = null }, { remove = null; model.deleteCategory(c.id) })
    }
}
