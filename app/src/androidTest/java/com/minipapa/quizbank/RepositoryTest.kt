package com.minipapa.quizbank

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.minipapa.quizbank.data.*
import com.minipapa.quizbank.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
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
