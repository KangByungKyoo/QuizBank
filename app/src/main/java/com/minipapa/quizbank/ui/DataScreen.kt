package com.minipapa.quizbank.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun DataScreen(bank: Bank, model: BankViewModel) {
    val resolver = LocalContext.current.contentResolver
    val csv by model.csv.collectAsStateWithLifecycle()
    val backup by model.backup.collectAsStateWithLifecycle()
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.readCsv(resolver, it) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.readBackup(resolver, it) } }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { model.export(resolver, it) } }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    Body {
        Heading("문제를 한 번에 가져오기")
        Hint("PC에서 작성한 UTF-8 CSV를 선택해 미리보기와 오류 행을 확인한 뒤 등록합니다.")
        Action("CSV 파일 선택", { importCsv.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) })
        Hint("헤더: ${CsvImport.header.joinToString(", ")}")
        Hint("유형: MULTIPLE / OX / SHORT\n객관식 정답: 보기 번호 1~5\n카테고리: IT/Python/내장함수\n따옴표 안의 쉼표, 줄바꿈, 큰따옴표를 지원합니다. 존재하지 않는 카테고리는 자동 생성합니다.")
        HorizontalDivider()
        Heading("백업과 복원")
        Hint("카테고리, 문제, 보기, 시험 이력과 오답 기록을 버전이 포함된 JSON 파일로 저장합니다.")
        Action("JSON 백업 저장", { exportJson.launch("QuizBank-backup-${java.time.LocalDate.now()}.json") })
        OutlinedButton({ importJson.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !LocalBusy.current) { Text("JSON 백업 파일 가져오기") }
        Hint("복원 전 중복 개수와 처리 방법을 확인할 수 있습니다. 파일 선택기로 직접 지정한 위치에 저장합니다. 가져오기는 파일당 10MB까지 지원합니다.")
    }
    csv?.let { preview ->
        AlertDialog(onDismissRequest = { model.csv.value = null }, title = { Text("CSV 미리보기") }, text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("등록 가능 ${preview.questions.size}개 · 오류 ${preview.errors.size}행") }
                items(preview.errors) { Text(it, color = MaterialTheme.colorScheme.error) }
                items(preview.questions, key = { it.question.id }) { row ->
                    HorizontalDivider(); Hint("${row.line}행 · ${row.categoryPath} · ${row.question.type.label}")
                    Text(row.question.content)
                    row.question.options.forEachIndexed { index, option -> Text("${index + 1}. $option") }
                    Text("정답: ${row.question.answerText}")
                    if (row.question.explanation.isNotEmpty()) Hint(row.question.explanation)
                }
            }
        }, confirmButton = { TextButton({ confirmImport = true }, enabled = preview.questions.isNotEmpty() && !LocalBusy.current) { Text("등록 확인") } },
            dismissButton = { TextButton({ model.csv.value = null }) { Text("취소") } })
        if (confirmImport) Confirm("${preview.questions.size}개 문제를 등록할까요?",
            "오류 ${preview.errors.size}행은 제외합니다. CSV를 반복해서 가져오면 같은 내용의 문제가 추가될 수 있습니다.",
            { confirmImport = false }, { confirmImport = false; model.importCsv() })
    }
    backup?.let { incoming ->
        val sameIds = incoming.questions.count { q -> bank.questions.any { it.id == q.id } }
        val sameCategories = incoming.categories.count { c -> bank.categories.any { it.id == c.id ||
            CategoryTree.path(bank.categories, it.id) == CategoryTree.path(incoming.categories, c.id) } }
        AlertDialog(onDismissRequest = { model.backup.value = null }, title = { Text("복원 미리보기") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("카테고리 ${incoming.categories.size}개 · 문제 ${incoming.questions.size}개 · 시험 ${incoming.sessions.size}회")
                Text("중복 카테고리 ${sameCategories}개 · 같은 ID 문제 ${sameIds}개")
                Hint("병합 시 같은 경로의 카테고리를 합칩니다. 문제 중복은 ID를 기준으로 판별하며 동일한 내용의 다른 ID 문제는 별개로 추가됩니다. 같은 ID 시험 이력은 유지합니다.")
                RestoreMode.entries.forEach { mode ->
                    Row { RadioButton(restoreMode == mode, { restoreMode = mode }); Text(mode.label, Modifier.padding(top = 12.dp)) }
                }
            }
        }, confirmButton = { TextButton({
            if (restoreMode == RestoreMode.REPLACE) confirmReplace = true else model.restore(restoreMode)
        }, enabled = !LocalBusy.current) { Text("복원") } }, dismissButton = { TextButton({ model.backup.value = null }) { Text("취소") } })
        if (confirmReplace) Confirm("현재 데이터를 모두 교체할까요?", "현재 카테고리 ${bank.categories.size}개, 문제 ${bank.questions.size}개, 시험 ${bank.sessions.size}회 기록을 지우고 선택한 백업으로 교체합니다. 필요하면 먼저 백업해 주세요.",
            { confirmReplace = false }, { confirmReplace = false; model.restore(RestoreMode.REPLACE) })
    }
}
