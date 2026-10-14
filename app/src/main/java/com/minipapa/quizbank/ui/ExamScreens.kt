package com.minipapa.quizbank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minipapa.quizbank.domain.*
import com.minipapa.quizbank.navigation.*

@Composable fun ExamSetupScreen(bank: Bank, model: BankViewModel) {
    var categories by rememberSaveable { mutableStateOf(listOf<String>()) }
    var count by rememberSaveable { mutableIntStateOf(5) }
    var wrongFirst by rememberSaveable { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    val typeSet = setOf(QuestionType.SHORT)
    val available = ExamEngine.candidates(bank, categories.toSet(), typeSet).size
    val start = { model.start(categories.toSet(), typeSet, count, wrongFirst, false) }
    Body {
        Heading("어떤 문제를 풀까요?")
        Hint("카테고리를 선택하지 않으면 전체에서 출제합니다. 상위 카테고리는 모든 하위 문제를 포함합니다.")
        if (categories.isNotEmpty()) TextButton({ categories = emptyList() }) { Text("카테고리 전체 선택으로 바꾸기") }
        CategoryTree.ordered(bank.categories).forEach { c ->
            Row {
                Checkbox(c.id in categories, { checked -> categories = if (checked) categories + c.id else categories - c.id })
                Text(CategoryTree.path(bank.categories, c.id), Modifier.padding(top = 12.dp))
            }
        }
        Heading("문제 유형")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionType.entries.forEach { type -> FilterChip(type in typeSet, {}, label = { Text(type.label) }, enabled = type.enabled) }
        }
        Hint("등록된 단답형 문제 내용이 출제되며, 등록된 정답으로 채점합니다.")
        Heading("문제 수")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, 0).forEach { n -> FilterChip(count == n, { count = n }, label = { Text(if (n == 0) "전체" else "${n}개") }) }
        }
        Heading("출제 방식")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!wrongFirst, { wrongFirst = false }, label = { Text("전체 랜덤") })
            FilterChip(wrongFirst, { wrongFirst = true }, label = { Text("오답 문제 중심") })
        }
        if (wrongFirst) Hint("틀린 문제를 먼저 출제하고 남은 자리는 다른 문제로 채웁니다. 각 그룹의 순서는 무작위입니다.")
        Card(Modifier.fillMaxWidth()) { Text("출제 가능 ${available}개 · 이번 시험 ${if (count == 0) available else minOf(count, available)}개",
            Modifier.padding(20.dp), fontWeight = FontWeight.Bold) }
        if (count > available && count != 0) Hint("요청한 ${count}개보다 문제가 적어 가능한 ${available}개로 출제합니다.")
        Action("시험 시작", { if (model.exam.value?.let { run -> bank.sessions.none { it.id == run.id } } == true) replace = true else start() }, Modifier.testTag("start-exam"), enabled = available > 0)
    }
    if (replace) Confirm("진행 중인 시험을 바꿀까요?", "현재 시험은 기록에 저장하지 않고 새 시험을 시작합니다.",
        { replace = false }, { replace = false; start() })
}
@Composable fun ExamScreen(run: ExamRun, model: BankViewModel) {
    val q = run.item.question
    var input by rememberSaveable(run.id, run.index) { mutableStateOf("") }
    Body {
        Hint("문제 ${run.index + 1} / ${run.items.size} · ${q.type.label}")
        LinearProgressIndicator(progress = { run.responses.size.toFloat() / run.items.size }, modifier = Modifier.fillMaxWidth())
        Hint(run.item.categoryPath)
        Heading(q.content)
        when (q.type) {
            QuestionType.MULTIPLE -> q.options.forEachIndexed { index, option ->
                OutlinedCard(onClick = { input = "$index" }, enabled = !run.answered && !LocalBusy.current,
                    modifier = Modifier.fillMaxWidth().testTag("exam-option-$index"),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (input == "$index") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp)) { RadioButton(input == "$index", null); Text("${index + 1}. $option", Modifier.padding(start = 8.dp, top = 12.dp)) }
                }
            }
            QuestionType.OX -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("O", "X").forEach { answer -> FilterChip(input == answer, { input = answer },
                    label = { Text(answer, style = MaterialTheme.typography.headlineMedium) }, enabled = !run.answered && !LocalBusy.current,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("exam-$answer")) }
            }
            QuestionType.SHORT -> OutlinedTextField(input, { input = it }, label = { Text("답을 입력해 주세요") }, enabled = !run.answered && !LocalBusy.current,
                modifier = Modifier.fillMaxWidth().testTag("exam-input"))
        }
        if (!run.answered) Action("답안 제출", { model.submit(input) }, Modifier.testTag("submit-answer"), enabled = input.isNotBlank())
        else {
            val response = run.responses[run.index]
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                if (response.correct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Heading(if (response.correct) "정답입니다!" else "다시 익혀 보세요")
                    Text("정답: ${q.answerText}")
                    if (q.explanation.isNotEmpty()) Text(q.explanation)
                    if (q.type == QuestionType.SHORT) {
                        Hint("표현이 달라도 의미가 맞다면 직접 정답 처리하세요.")
                        OutlinedButton(model::toggleCorrect, enabled = !LocalBusy.current) { Text(if (response.correct) "오답으로 변경" else "정답으로 변경") }
                    }
                }
            }
            Action(if (run.index == run.items.lastIndex) "시험 완료 및 결과 저장" else "다음 문제", model::next, modifier = Modifier.testTag("next-question"))
        }
    }
}
@Composable fun ResultsScreen(run: ExamRun, model: BankViewModel) {
    Body {
        Heading("이번 학습을 마쳤어요")
        Text("${percent(run.correctCount, run.items.size)}%", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text("정답 ${run.correctCount}개 · 오답 ${run.items.size - run.correctCount}개 · 총 ${run.items.size}개")
        Hint("시험 결과와 오답 기록을 기기에 저장했습니다.")
        Action("처음 화면으로", { model.navigate(Page.HOME) })
        OutlinedButton({ model.navigate(Page.WRONG) }, Modifier.fillMaxWidth()) { Text("오답 노트 열기") }
        Heading("이번에 틀린 문제")
        if (run.correctCount == run.items.size) Hint("모든 문제를 맞혔습니다.")
        run.items.forEachIndexed { index, item ->
            if (!run.responses[index].correct) OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.question.content, fontWeight = FontWeight.Bold)
                    Text("정답: ${item.question.answerText}")
                    if (item.question.explanation.isNotEmpty()) Text(item.question.explanation)
                }
            }
        }
    }
}
