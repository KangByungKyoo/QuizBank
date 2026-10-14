package com.minipapa.quizbank.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DomainTest {
    @Test fun utf8AndKoreanExcelCsvKeepSingleLevelCategoryWithInternalSpaces() {
        val text = CsvImport.header.joinToString(",") +
            "\r\n 회사 업무 ,SHORT,\"다음 설명에 해당하는 용어는?\n개념 검증\",,,,,,PoC,\"첫째 줄\n둘째 줄\""
        val encodings = listOf(text.toByteArray(Charsets.UTF_8),
            ("\uFEFF" + text).toByteArray(Charsets.UTF_8), text.toByteArray(java.nio.charset.Charset.forName("MS949")))
        encodings.forEach { bytes ->
            val decoded = CsvImport.decode(bytes)
            assertEquals(text, decoded)
            val preview = CsvImport.preview(decoded)
            assertTrue(preview.errors.toString(), preview.errors.isEmpty())
            val imported = preview.questions.single()
            assertEquals("회사 업무", imported.categoryPath)
            assertEquals("PoC", imported.question.answer)
            assertEquals("첫째 줄\n둘째 줄", imported.question.explanation)
        }
    }
    @Test fun invalidCsvEncodingGivesActionableMessageInsteadOfInputLength() {
        val invalidInputs = listOf(byteArrayOf(0xFF.toByte()),
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0xBB.toByte()))
        invalidInputs.forEach { bytes ->
            val error = runCatching { CsvImport.decode(bytes) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("CSV UTF-8"))
            assertFalse(error.message!!.contains("Input length"))
        }
    }
    @Test fun csvPreservesDescriptionQuestionTermAnswerAndMultilineExplanation() {
        val explanation = "PoC는 Proof of Concept(개념 검증)의 약자다.\n대규모 개발에 앞서 핵심 기능이 실제로 가능한지 소규모로 시험한다.\n완성품 개발보다는 기술적 위험과 불확실성을 줄이는 데 목적이 있다."
        val description = "새 아이디어나 기술의 실현 가능성을 확인하는 개념 검증"
        val content = "다음 설명에 해당하는 용어는 무엇인가요?\n\n$description"
        val csv = CsvImport.header.joinToString(",") + "\nIT,SHORT,\"$content\",,,,,,PoC,\"$explanation\""
        val preview = CsvImport.preview(csv)
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        val q = preview.questions.single().question
        assertEquals(content, q.content)
        assertEquals("PoC", q.answer)
        assertEquals(explanation, q.explanation)
        assertEquals(q, ExamEngine.select(Bank(questions = listOf(q)), emptySet(), setOf(QuestionType.SHORT), 0, false).single())
        assertTrue(q.correct(" poc "))
        assertFalse(q.correct(description))
    }
    @Test fun definitionQuestionsAreNotAutomaticallyReversedOrRewritten() {
        listOf("PoC란 무엇인가?", "PoC이란 무엇인가?", "PoC는 무엇인가요?", "PoC은 무엇인가？").forEach { content ->
            val original = question("poc").copy(content = content, answer = "개념 검증", explanation = "해설")
            val bank = Bank(categories, listOf(original))
            val q = ExamEngine.select(bank, emptySet(), setOf(QuestionType.SHORT), 0, false).single()
            assertEquals(original, q)
            assertEquals(original, bank.questions.single())
            assertTrue(ExamRun(items = listOf(ExamItem(q, "IT"))).submit("개념 검증").responses.single().correct)
        }
    }
    @Test fun convertedItTermsFileImportsAll34QuestionsWithoutChangingTheirFields() {
        val text = java.io.File("../examples/회사_업무_IT용어_34개_단답형.csv").readText(Charsets.UTF_8)
        val rows = CsvImport.rows(text).drop(1)
        val preview = CsvImport.preview(text)
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(34, preview.questions.size)
        rows.zip(preview.questions).forEach { (row, imported) ->
            assertEquals(row.fields[0], imported.categoryPath)
            assertEquals(QuestionType.SHORT, imported.question.type)
            assertEquals(row.fields[2], imported.question.content)
            assertEquals(row.fields[8], imported.question.answer)
            assertEquals(row.fields[9], imported.question.explanation)
        }
        assertEquals("PoC", preview.questions.first().question.answer)
        assertTrue(preview.questions.all { it.question.content.startsWith("다음 설명에 해당하는 용어는 무엇인가요?") })
    }
    private val categories = listOf(Category("root", "IT"), Category("child", "Python", "root"), Category("leaf", "기초", "child"), Category("other", "성경"))
    private fun question(id: String, category: String = "leaf", type: QuestionType = QuestionType.SHORT) =
        Question(id, category, type, "문제 $id", if (type == QuestionType.MULTIPLE) listOf("첫째", "둘째") else emptyList(),
            when (type) { QuestionType.MULTIPLE -> "1"; QuestionType.OX -> "O"; QuestionType.SHORT -> " HTML " })
    private fun fails(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }
    @Test fun hierarchyIncludesEveryDescendantAndRejectsCyclesAndDuplicates() {
        assertEquals(setOf("root", "child", "leaf"), CategoryTree.descendants(categories, setOf("root")))
        assertEquals("IT/Python/기초", CategoryTree.path(categories, "leaf"))
        fails { CategoryTree.validate(categories + Category("duplicate", "IT")) }
        fails { CategoryTree.validate(listOf(Category("a", "A", "b"), Category("b", "B", "a"))) }
        fails { CategoryTree.validate(listOf(Category("a", "A", "missing"))) }
    }
    @Test fun deepCategoriesAreTraversedWithoutRecursion() {
        val deep = (0..1200).map { Category("$it", "단계$it", if (it == 0) null else "${it - 1}") }
        CategoryTree.validate(deep)
        assertEquals(1201, CategoryTree.descendants(deep, setOf("0")).size)
        assertTrue(CategoryTree.path(deep, "1200").endsWith("단계1200"))
    }
    @Test fun multipleChoiceValidationAndOriginalOrderArePreserved() {
        val q = question("q", type = QuestionType.MULTIPLE)
        q.validate(); assertEquals("둘째", q.answerText)
        fails { q.copy(options = listOf("하나")).validate() }
        fails { q.copy(options = listOf("하나", "")).validate() }
        fails { q.copy(answer = "2").validate() }
        val result = ExamEngine.select(Bank(categories, listOf(q)), setOf("root"), setOf(QuestionType.MULTIPLE), 5, false)
        assertTrue(result.isEmpty())
    }
    @Test fun onlyShortAnswersAreSelectedAndRegisteredContentAndAnswerArePreserved() {
        val short = question("short").copy(content = "등록된 문제\n원문", answer = "등록된 정답")
        val bank = Bank(categories, listOf(short, question("mc", type = QuestionType.MULTIPLE), question("ox", type = QuestionType.OX)))
        val selected = ExamEngine.select(bank, emptySet(), QuestionType.entries.toSet(), 0, false)
        assertEquals(listOf(short), selected)
        val run = ExamRun(items = selected.map { ExamItem(it, "IT/Python/기초") })
        assertEquals("등록된 문제\n원문", run.item.question.content)
        assertTrue(run.submit("등록된 정답").responses.single().correct)
        assertFalse(run.submit("다른 답").responses.single().correct)
    }
    @Test fun shortAnswerTrimsAndIgnoresEnglishCaseButNotInternalSpaces() {
        val q = question("q")
        assertTrue(q.correct("html")); assertTrue(q.correct(" HTML  "))
        assertFalse(q.correct("HT ML"))
        assertTrue(question("ox", type = QuestionType.OX).correct("O"))
        assertFalse(question("ox", type = QuestionType.OX).correct("X"))
    }
    @Test fun examsUseSelectedTypesSubtreesAndNeverRepeatQuestions() {
        val questions = (1..30).map { question("q$it", if (it % 2 == 0) "leaf" else "other") }
        repeat(20) { seed ->
            val result = ExamEngine.select(Bank(categories, questions), setOf("root", "child"), setOf(QuestionType.SHORT), 20, false, random = Random(seed))
            assertEquals(15, result.size); assertEquals(15, result.map { it.id }.toSet().size)
            assertTrue(result.all { it.categoryId == "leaf" })
        }
        assertTrue(ExamEngine.select(Bank(categories, questions), emptySet(), setOf(QuestionType.OX), 5, false).isEmpty())
    }
    @Test fun wrongFocusedFillsWithOtherQuestionsAndWrongOnlyDoesNot() {
        val qs = (1..8).map { question("q$it") }
        val history = listOf(AnswerHistory("a", "s", "q5", "leaf", "IT/Python/기초", "문제", "입력", "정답", "", false, QuestionType.SHORT))
        val bank = Bank(categories, qs, answers = history)
        assertEquals("q5", ExamEngine.select(bank, emptySet(), QuestionType.entries.toSet(), 5, true).first().id)
        assertEquals(listOf("q5"), ExamEngine.select(bank, emptySet(), QuestionType.entries.toSet(), 0, false, true).map { it.id })
    }
    @Test fun manualShortAnswerOverrideAndExamStateRoundTrip() {
        var run = ExamRun(items = listOf(ExamItem(question("q"), "IT/Python")))
        assertEquals(run, run.next())
        run = run.submit("잘못된 표현")
        assertFalse(run.responses.single().correct)
        assertEquals(run, run.submit("HTML"))
        run = run.toggle()
        assertTrue(run.finished); assertEquals(1, run.correctCount)
        assertEquals(run, JsonCodec.readExam(JsonCodec.exam(run)))
    }
    @Test fun csvSupportsBomQuotesCommasNewlinesAndAllTypes() {
        val text = "\uFEFF" + CsvImport.header.joinToString(",") + "\r\n" +
            "IT/Python,MULTIPLE,\"쉼표, 따옴표 \"\"와\"\"\n줄바꿈\",첫째,둘째,,,,2,해설\r\n" +
            "성경,OX,O/X 문제,,,,,,O,\r\n" + "IT,SHORT,영문 약자,,,,,,HTML,설명"
        val preview = CsvImport.preview(text)
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(3, preview.questions.size)
        assertEquals("쉼표, 따옴표 \"와\"\n줄바꿈", preview.questions[0].question.content)
        assertEquals("둘째", preview.questions[0].question.answerText)
        assertEquals(4, preview.questions[1].line)
    }
    @Test fun csvInvalidRowsAreReportedWithoutDiscardingGoodRows() {
        val text = CsvImport.header.joinToString(",") + "\nIT,SHORT,좋은 문제,,,,,,정답,\nIT,UNKNOWN,유형 오류,,,,,,답,\nIT,MULTIPLE,정답 오류,A,B,,,,5,"
        val preview = CsvImport.preview(text)
        assertEquals(1, preview.questions.size); assertEquals(2, preview.errors.size)
        assertTrue(preview.errors[0].startsWith("3행"))
        assertTrue(CsvImport.preview(CsvImport.header.joinToString(",") + "\n\"닫히지 않음").errors.isNotEmpty())
    }
    @Test fun backupRoundTripPreservesKoreanOptionsAndLearningHistory() {
        val q = question("q", type = QuestionType.MULTIPLE)
        val bank = Bank(categories, listOf(q), listOf(SessionSummary("s", 1, 2, 1, 0)),
            listOf(AnswerHistory("a", "s", "q", "leaf", "IT/Python/기초", q.content, "첫째", "둘째", "해설", false, q.type)))
        assertEquals(bank, JsonCodec.readBackup(JsonCodec.backup(bank)))
        fails { JsonCodec.readBackup(JsonCodec.backup(bank).replace("\"version\": 1", "\"version\": 99")) }
    }
    @Test fun backupRejectsDanglingReferencesAndIncorrectTotals() {
        fails { JsonCodec.validate(Bank(categories, listOf(question("q", "missing")))) }
        fails { JsonCodec.validate(Bank(categories, sessions = listOf(SessionSummary("s", 1, 2, 1, 1)))) }
    }
    @Test fun restoreSkipsOrOverwritesByIdAndMergesCategoryPaths() {
        val q = question("q")
        val current = Bank(categories, listOf(q))
        val incoming = Bank(listOf(Category("different-root", "IT"), Category("different-child", "Python", "different-root"),
            Category("different-leaf", "기초", "different-child")), listOf(q.copy(categoryId = "different-leaf", content = "바뀐 문제")))
        val merged = RestorePlan.build(current, incoming, RestoreMode.MERGE)
        assertEquals(4, merged.categories.size); assertEquals(q, merged.questions.single())
        val overwritten = RestorePlan.build(current, incoming, RestoreMode.OVERWRITE)
        assertEquals("바뀐 문제", overwritten.questions.single().content)
        assertEquals("leaf", overwritten.questions.single().categoryId)
        assertEquals(incoming, RestorePlan.build(current, incoming, RestoreMode.REPLACE))
    }
}
