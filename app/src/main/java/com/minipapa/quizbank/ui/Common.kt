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
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.text.style.TextOverflow
import com.minipapa.quizbank.domain.*

val LocalBusy = compositionLocalOf { false }
@Composable fun QuizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF14675B), onPrimary = Color.White,
        primaryContainer = Color(0xFFD3EDE5), onPrimaryContainer = Color(0xFF123E35),
        secondary = Color(0xFF4C635B), secondaryContainer = Color(0xFFD3EDE5),
        onSecondaryContainer = Color(0xFF123E35), surfaceVariant = Color(0xFFEDF2EF),
        surfaceContainer = Color(0xFFF0F5F2),
        background = Color(0xFFF5F8F7), surface = Color(0xFFFAFDFC)), content = content)
}
@Composable fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
@Composable fun Hint(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
@Composable fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, minHeight: Dp = 52.dp) {
    val keyboard = LocalSoftwareKeyboardController.current
    Button({ keyboard?.hide(); onClick() }, modifier.fillMaxWidth().heightIn(min = minHeight), enabled = enabled && !LocalBusy.current) { Text(text) }
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
    allowAll: Boolean = false, label: String = "카테고리", minHeight: Dp = 52.dp,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, modifier.fillMaxWidth().heightIn(min = minHeight), enabled = !LocalBusy.current,
        shape = if (compact) RoundedCornerShape(10.dp) else ButtonDefaults.outlinedShape,
        contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding) {
        Text("$label: ${if (selected == null) if (allowAll) "전체" else "선택해 주세요" else CategoryTree.path(categories, selected)}",
            modifier = if (compact) Modifier.weight(1f) else Modifier,
            maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            color = if (compact) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
        if (compact) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
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
