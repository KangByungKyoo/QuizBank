package com.minipapa.quizbank.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*
import com.minipapa.quizbank.printing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PrintSettingsScreen(bank: Bank, model: BankViewModel) {
    val controller = model.printExport
    val options by controller.options.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val available = remember(bank, options) { PrintSelector.candidates(bank, options).size }
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    val categoryLabel = if (options.categoryIds.isEmpty()) "전체 카테고리" else "${options.categoryIds.size}개 선택"
    PrintScreenHost(model) { actions ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("학습한 문제를 A4 시험지로 출력해 보세요.", style = MaterialTheme.typography.titleSmall)
                        Text("문제지와 정답·해설지를 따로 저장하거나 인쇄할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("카테고리", Modifier.weight(1f))
                            OutlinedButton({ categoriesOpen = true }, enabled = !busy, modifier = Modifier.testTag("print-categories")) { Text(categoryLabel) }
                        }
                        PrintCheck("하위 카테고리 포함", options.includeDescendants, !busy) { controller.update(options.copy(includeDescendants = it)) }
                        PrintChoice("문제 유형", if (options.types.size == QuestionType.entries.size) "전체" else options.types.singleOrNull()?.label.orEmpty(),
                            listOf("전체" to QuestionType.entries.toSet()) + QuestionType.entries.map { it.label to setOf(it) }, !busy) {
                            controller.update(options.copy(types = it))
                        }
                        PrintChoice("문제 수", if (options.count == 0) "전체 문제" else "${options.count}문제",
                            listOf("10문제" to 10, "20문제" to 20, "50문제" to 50, "전체 문제" to 0), !busy) { controller.update(options.copy(count = it)) }
                        PrintChoice("문제 순서", if (options.randomOrder) "무작위 순서" else "등록 순서",
                            listOf("등록 순서" to false, "무작위 순서" to true), !busy) { controller.update(options.copy(randomOrder = it)) }
                    }
                }
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        PrintCheck("정답지 포함", options.includeAnswers, !busy) { controller.update(options.copy(includeAnswers = it)) }
                        PrintCheck("해설 포함", options.includeExplanations, !busy && options.includeAnswers) { controller.update(options.copy(includeExplanations = it)) }
                        PrintChoice("답안 작성 공간", "${options.answerLines}줄", (1..3).map { "${it}줄" to it }, !busy) { controller.update(options.copy(answerLines = it)) }
                    }
                }
                Text("출력 가능 ${available}문제 · 이번 출력 ${if (options.count == 0) available else minOf(options.count, available)}문제",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                if (options.count > available && available > 0) Hint("요청한 ${options.count}문제보다 적어 가능한 ${available}문제로 출력합니다.")
                if (available == 0) Hint("출력할 문제가 없습니다. 카테고리·유형을 바꾸거나 문제 내용을 완성해 주세요.")
                Hint("문제 내용·카테고리·정답이 완성된 문제만 출력합니다. 학습 기록은 변경하지 않습니다.")
                val generated by controller.generated.collectAsStateWithLifecycle()
                if (generated != null) TextButton({ controller.generated.value = null }, enabled = !busy) { Text("문제 다시 선택하기") }
            }
            actions(available > 0)
        }
    }
    if (categoriesOpen) {
        var selected by remember { mutableStateOf(options.categoryIds) }
        AlertDialog(onDismissRequest = { categoriesOpen = false }, title = { Text("출력 카테고리") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    PrintCheck("전체 카테고리", selected.isEmpty(), true) { if (it) selected = emptySet() }
                    CategoryTree.ordered(bank.categories).forEach { category ->
                        PrintCheck(CategoryTree.path(bank.categories, category.id), category.id in selected, true) {
                            selected = if (it) selected + category.id else selected - category.id
                        }
                    }
                }
            }, confirmButton = { TextButton({ controller.update(options.copy(categoryIds = selected)); categoriesOpen = false }) { Text("적용") } },
            dismissButton = { TextButton({ categoriesOpen = false }) { Text("취소") } })
    }
}

@Composable
private fun PrintCheck(label: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> PrintChoice(label: String, selected: String, choices: List<Pair<String, T>>, enabled: Boolean, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1.15f)) {
            OutlinedButton({ open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$selected ▾") }
            DropdownMenu(open, { open = false }) {
                choices.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); open = false }) }
            }
        }
    }
}

/** The same export controls operate on settings and preview; file picker returns safely after rotation. */
@Composable
private fun PrintScreenHost(model: BankViewModel, content: @Composable (@Composable (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val controller = model.printExport
    val busy by controller.busy.collectAsStateWithLifecycle()
    val message by controller.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    val batch by controller.generated.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        controller.save(context.contentResolver, uri, controller.generated.value?.documents?.firstOrNull { it.file.absolutePath == pendingFile })
        pendingFile = null
    }
    LaunchedEffect(message) {
        message?.let {
            val saved = controller.savedUri.value.takeIf { _ -> it.contains("PDF를 저장했습니다") }
            val result = snack.showSnackbar(it, actionLabel = if (saved != null) "열기" else null)
            if (result == SnackbarResult.ActionPerformed && saved != null) runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.onFailure { controller.message.value = "PDF를 열 수 있는 앱이 없습니다. 파일 앱에서 확인해 주세요." }
            if (controller.message.value == it) controller.message.value = null
        }
    }
    fun selectDocument(document: GeneratedPdf) {
        val current = batch ?: return
        if (action == "save") {
            pendingFile = document.file.absolutePath
            launcher.launch(current.filename(document))
        } else {
            runCatching {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print("QuizBank ${document.kind.label}", QuizPrintAdapter(current, document, context.cacheDir),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
            }.onFailure { controller.message.value = "인쇄를 시작하지 못했습니다: ${it.localizedMessage}" }
        }
        action = null
    }
    Box(Modifier.fillMaxSize()) {
        content { enabled ->
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ controller.prepare(context.cacheDir) { model.navigate(Page.PRINT_PREVIEW) } }, enabled = enabled && !busy,
                            modifier = Modifier.testTag("print-preview")) { Text("미리보기") }
                        Button({ controller.prepare(context.cacheDir) { action = "save" } }, enabled = enabled && !busy,
                            modifier = Modifier.testTag("print-save")) { Text("PDF 저장") }
                        OutlinedButton({ controller.prepare(context.cacheDir) { action = "print" } }, enabled = enabled && !busy,
                            modifier = Modifier.testTag("print-start")) { Text("인쇄") }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }
    if (action != null && batch != null) AlertDialog(onDismissRequest = { action = null }, title = { Text(if (action == "save") "저장할 PDF" else "인쇄할 문서") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("문제지와 정답·해설지는 각각 별도로 ${if (action == "save") "저장" else "인쇄"}합니다.")
                batch!!.documents.forEach { document ->
                    OutlinedButton({ selectDocument(document) }, Modifier.fillMaxWidth()) { Text("${document.kind.label} · ${document.pages}페이지") }
                }
            }
        }, confirmButton = { TextButton({ action = null }) { Text("닫기") } })
}

@Composable
fun PrintPreviewScreen(model: BankViewModel) {
    val batch by model.printExport.generated.collectAsStateWithLifecycle()
    val current = batch
    if (current == null) {
        Body { Hint("출력 설정에서 미리보기를 다시 생성해 주세요."); Action("출력 설정", { model.navigate(Page.PRINT) }) }
        return
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var zoom by rememberSaveable { mutableStateOf(false) }
    val document = current.documents[selected.coerceIn(current.documents.indices)]
    PrintScreenHost(model) { actions ->
        Column(Modifier.fillMaxSize()) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.documents.forEachIndexed { index, pdf ->
                    FilterChip(selected == index, { selected = index }, label = { Text("${pdf.kind.label} (${pdf.pages})") })
                }
                FilterChip(zoom, { zoom = !zoom }, label = { Text("확대") })
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                val pageWidth = maxWidth * if (zoom) 1.6f else 1f
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    key(document.file.absolutePath) {
                        LazyColumn(Modifier.width(pageWidth).fillMaxHeight(), contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items((0 until document.pages).toList(), key = { it }) { index ->
                                PdfPreviewPage(document, index)
                            }
                        }
                    }
                }
            }
            actions(true)
        }
    }
}

@Composable
private fun PdfPreviewPage(document: GeneratedPdf, index: Int) {
    var error by remember(document.file, index) { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(null, document.file, index) {
        try {
            value = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer -> renderer.openPage(index).use { page ->
                        Bitmap.createBitmap(1000, (1000f * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "페이지를 열지 못했습니다: ${e.localizedMessage}" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${index + 1} / ${document.pages} 페이지", style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().aspectRatio(595f / 842f).background(Color.White).testTag("pdf-preview-page-$index"), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "${document.kind.label} ${index + 1}페이지", modifier = Modifier.fillMaxSize()) }
                ?: if (error != null) Text(error!!, color = Color.Black, modifier = Modifier.padding(12.dp)) else CircularProgressIndicator()
        }
    }
}
