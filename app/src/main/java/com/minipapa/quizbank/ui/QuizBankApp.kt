package com.minipapa.quizbank.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipapa.quizbank.navigation.*

@Composable fun QuizBankApp(model: BankViewModel) {
    val bank by model.bank.collectAsStateWithLifecycle()
    val page by model.page.collectAsStateWithLifecycle()
    val exam by model.exam.collectAsStateWithLifecycle()
    val editing by model.editing.collectAsStateWithLifecycle()
    val loaded by model.loaded.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var leave by rememberSaveable { mutableStateOf(false) }
    fun back() {
        if (page == Page.EXAM || page == Page.EDIT) leave = true else model.navigate(Page.HOME)
    }
    BackHandler(page != Page.HOME) { back() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); model.message.value = null } }
    CompositionLocalProvider(LocalBusy provides busy) {
        Scaffold(bottomBar = { SnackbarHost(snack, Modifier.navigationBarsPadding()) }, topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (page != Page.HOME) TextButton({ back() }, enabled = !busy) { Text("‹ 뒤로") }
                    Text(if (page == Page.EDIT && editing != null) "문제 수정" else page.title,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    if (!loaded) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    else when (page) {
                        Page.HOME -> HomeScreen(bank, exam?.takeUnless { run -> bank.sessions.any { it.id == run.id } }, model)
                        Page.CATEGORIES -> CategoriesScreen(bank, model)
                        Page.QUESTIONS -> QuestionsScreen(bank, model, false)
                        Page.WRONG -> QuestionsScreen(bank, model, true)
                        Page.EDIT -> QuestionEditor(bank, editing, model::save, model::cancelEdit)
                        Page.EXAM_SETUP -> ExamSetupScreen(bank, model)
                        Page.EXAM -> exam?.let { ExamScreen(it, model) }
                        Page.RESULTS -> exam?.let { ResultsScreen(it, model) }
                        Page.STATS -> StatsScreen(bank)
                        Page.SETTINGS -> DataScreen(bank, model)
                    }
                }
            }
        }
        if (leave) Confirm(if (page == Page.EDIT) "작성 내용을 취소할까요?" else "시험을 중단할까요?",
            if (page == Page.EDIT) "저장하지 않은 변경은 사라집니다." else "완료하지 않은 시험은 학습 기록에 저장하지 않습니다.",
            { leave = false }, { leave = false; if (page == Page.EDIT) model.cancelEdit() else model.abandon() })
    }
}
