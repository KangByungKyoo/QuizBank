package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*

val LocalBusy = compositionLocalOf { false }
@Composable fun QuizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF14675B), onPrimary = Color.White,
        primaryContainer = Color(0xFFD3EDE5), onPrimaryContainer = Color(0xFF123E35),
        secondary = Color(0xFF596C86), secondaryContainer = Color(0xFFE0E8F4),
        background = Color(0xFFF5F8F7), surface = Color(0xFFFAFDFC)), content = content)
}
@Composable fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
@Composable fun Hint(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
@Composable fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    Button({ keyboard?.hide(); onClick() }, modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled && !LocalBusy.current) { Text(text) }
}
@Composable fun Tile(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = !LocalBusy.current, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Hint(subtitle)
        }
    }
}
@Composable fun Confirm(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm, enabled = !LocalBusy.current) { Text("확인") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } })
}
@Composable fun CategoryPicker(categories: List<Category>, selected: String?, onSelect: (String?) -> Unit,
    allowAll: Boolean = false, label: String = "카테고리") {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !LocalBusy.current) {
        Text("$label: ${if (selected == null) if (allowAll) "전체" else "선택해 주세요" else CategoryTree.path(categories, selected)}")
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (allowAll) TextButton({ onSelect(null); open = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("전체 / 최상위") }
            CategoryTree.ordered(categories).forEach { c ->
                TextButton({ onSelect(c.id); open = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(CategoryTree.path(categories, c.id), Modifier.fillMaxWidth())
                }
            }
            if (categories.isEmpty()) Text("먼저 카테고리를 추가해 주세요.")
        }
    }, confirmButton = { TextButton({ open = false }) { Text("닫기") } })
}
fun percent(correct: Int, total: Int) = if (total == 0) 0 else correct * 100 / total
