package com.minipapa.quizbank

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.minipapa.quizbank.data.*
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    @Test fun answerOnlyQuestionsPersistAndRestoreWithoutLeavingStaleDraftQuestions(): Unit = runBlocking {
        val name = "not-ready-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, BankDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            var repo = BankRepository(db)
            val draft = Question("draft", "", QuestionType.SHORT, "", answer = "PoC")
            repo.saveQuestion(draft)
            db.close()
            db = Room.databaseBuilder(context, BankDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
            repo = BankRepository(db)
            assertEquals(draft, repo.snapshot().questions.single())
            val backup = JsonCodec.readBackup(JsonCodec.backup(repo.snapshot()))
            RestoreMode.entries.forEach { mode ->
                repo.restore(backup, mode)
                assertEquals(listOf(draft), repo.snapshot().questions)
            }
            repo.saveCategory(Category("c", "IT"))
            repo.saveQuestion(draft.copy(categoryId = "c", content = "개념 검증의 약자는?"))
            assertTrue(repo.snapshot().questions.single().isReady)
            repo.restore(backup, RestoreMode.OVERWRITE)
            assertFalse(repo.snapshot().questions.single().isReady)
            repo.restore(Bank(), RestoreMode.REPLACE)
            assertTrue(repo.snapshot().questions.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationFromVersionOnePreserves38QuestionsOptionsHistoryAndExamDraft(): Unit = runBlocking {
        val name = "migration-${System.nanoTime()}.db"
        val old = context.openOrCreateDatabase(name, 0, null)
        val schema = org.json.JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.minipapa.quizbank.data.BankDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val category = Category("c", "IT")
        val multiple = Question("q0", "c", QuestionType.MULTIPLE, "원본 문제", listOf("첫째", "둘째"), "1", "원본 해설", 10L, 20L)
        val questions = listOf(multiple) + (1..37).map { Question("q$it", "c", QuestionType.SHORT, "문제 $it", answer = "답 $it", createdAt = 10L, updatedAt = 20L) }
        val session = SessionSummary("s", 10L, 20L, 1, 0)
        val history = AnswerHistory("a", "s", "q0", "c", "IT", multiple.content, "첫째", "둘째", "원본 해설", false, QuestionType.MULTIPLE)
        val draft = ExamRun(items = listOf(ExamItem(questions[1], "IT")))
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories VALUES (?, ?, ?)", arrayOf("c", "IT", null))
            questions.forEach { q -> old.execSQL("INSERT INTO questions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(q.id, q.categoryId, q.type.name, q.content, q.answer, q.explanation, q.createdAt, q.updatedAt, 0)) }
            multiple.options.forEachIndexed { i, text -> old.execSQL("INSERT INTO question_options VALUES (?, ?, ?)", arrayOf("q0", i, text)) }
            old.execSQL("INSERT INTO exam_sessions VALUES (?, ?, ?, ?, ?)", arrayOf("s", 10L, 20L, 1, 0))
            old.execSQL("INSERT INTO exam_answers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("a", "s", "q0", "c", "IT", multiple.content, "첫째", "둘째", "원본 해설", 0, "MULTIPLE"))
            old.execSQL("INSERT INTO exam_draft VALUES (?, ?)", arrayOf(1, JsonCodec.exam(draft)))
            old.version = 1
        } finally { old.close() }
        val upgraded = Room.databaseBuilder(context, BankDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val repo = BankRepository(upgraded)
            val bank = repo.snapshot()
            assertEquals(listOf(category), bank.categories)
            assertEquals(questions.toSet(), bank.questions.toSet())
            assertEquals(listOf(session), bank.sessions)
            assertEquals(listOf(history), bank.answers)
            assertEquals(draft, repo.draft())
            repo.saveQuestion(Question("new-draft", "", QuestionType.SHORT, "", answer = "정답만"))
            assertEquals(39, repo.snapshot().questions.size)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun csvImportCreatesHierarchyAndCompletedExamSurvivesQuestionDeletion(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            val csv = CsvImport.header.joinToString(",") + "\nIT/Python/기초,MULTIPLE,길이 함수,len(),size(),,,,1,해설\nIT/Linux,OX,리눅스 문제,,,,,,X,"
            assertEquals(2, repo.importCsv(CsvImport.preview(csv)))
            val bank = repo.snapshot()
            assertEquals(4, bank.categories.size)
            val root = bank.categories.first { it.name == "IT" }
            assertEquals(0, ExamEngine.candidates(bank, setOf(root.id), QuestionType.entries.toSet()).size)
            val q = bank.questions.first { it.type == QuestionType.MULTIPLE }
            val run = ExamRun(items = listOf(ExamItem(q, CategoryTree.path(bank.categories, q.categoryId)))).submit("1")
            repo.complete(run); repo.complete(run)
            assertEquals(1, repo.snapshot().sessions.size)
            assertEquals(1, repo.snapshot().wrongCount(q.id))
            repo.saveQuestion(q.copy(options = listOf("원본 새 보기", "둘째"), updatedAt = q.updatedAt + 1))
            assertEquals("len()", repo.snapshot().answers.single().expected)
            repo.deleteCategory(root.id)
            val deleted = repo.snapshot()
            assertTrue(deleted.categories.isEmpty()); assertTrue(deleted.questions.isEmpty())
            assertNull(deleted.answers.single().questionId); assertNull(deleted.answers.single().categoryId)
            assertEquals("길이 함수", deleted.answers.single().question)
            assertEquals(1, deleted.sessions.size)
            assertEquals(deleted, JsonCodec.readBackup(JsonCodec.backup(deleted)))
        } finally { db.close() }
    }
    @Test fun backupMergeDoesNotDuplicateAndInvalidRestoreKeepsExistingData(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            assertEquals(5, repo.addSamples()); assertEquals(0, repo.addSamples())
            val original = repo.snapshot()
            repo.restore(JsonCodec.readBackup(JsonCodec.backup(original)), RestoreMode.MERGE)
            assertEquals(5, repo.snapshot().questions.size)
            val changed = original.copy(questions = original.questions.map { it.copy(content = "수정 ${it.content}") })
            repo.restore(changed, RestoreMode.OVERWRITE)
            assertTrue(repo.snapshot().questions.all { it.content.startsWith("수정 ") })
            val beforeInvalid = repo.snapshot()
            val invalid = original.copy(categories = listOf(Category("bad", "잘못된 카테고리", "missing")))
            assertTrue(runCatching { repo.restore(invalid, RestoreMode.REPLACE) }.isFailure)
            assertEquals(beforeInvalid, repo.snapshot())
            repo.restore(original, RestoreMode.REPLACE)
            assertEquals(original.questions.toSet(), repo.snapshot().questions.toSet())
        } finally { db.close() }
    }
    @Test fun categoriesQuestionsAndWrongHistorySurviveDatabaseReopen(): Unit = runBlocking {
        val name = "persistence-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, BankDatabase::class.java, name).build()
        try {
            var repo = BankRepository(db)
            repo.addSamples()
            val bank = repo.snapshot(); val q = bank.questions.first { it.type == QuestionType.SHORT }
            val run = ExamRun(items = listOf(ExamItem(q, "샘플 문제"))).submit("오답")
            repo.saveDraft(run)
            repo.complete(run)
            db.close()
            db = Room.databaseBuilder(context, BankDatabase::class.java, name).build()
            repo = BankRepository(db)
            assertEquals(5, repo.snapshot().questions.size)
            assertEquals(1, repo.snapshot().sessions.size)
            assertEquals(1, repo.snapshot().wrongCount(q.id))
            assertEquals(run, repo.draft())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun categoryMovesRejectCyclesAndDeepDeletionAvoidsTriggerLimit(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java).build()
        try {
            val repo = BankRepository(db)
            val categories = (0..1050).map { Category("$it", "카테고리$it", if (it == 0) null else "${it - 1}") }
            repo.restore(Bank(categories), RestoreMode.REPLACE)
            assertTrue(runCatching { repo.saveCategory(categories[0].copy(parentId = "1050")) }.isFailure)
            assertEquals(1051, repo.snapshot().categories.size)
            repo.deleteCategory("0")
            assertTrue(repo.snapshot().categories.isEmpty())
        } finally { db.close() }
    }
}
