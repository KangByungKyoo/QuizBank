package com.minipapa.quizbank.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("parentId")])
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val parentId: String?)
@Entity(tableName = "questions", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)], indices = [Index("categoryId")])
data class QuestionEntity(@PrimaryKey val id: String, val categoryId: String, val type: String,
    val content: String, val answer: String, val explanation: String, val createdAt: Long, val updatedAt: Long, val sample: Boolean)
@Entity(tableName = "question_options", primaryKeys = ["questionId", "position"], foreignKeys = [ForeignKey(
    entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("questionId")])
data class QuestionOptionEntity(val questionId: String, val position: Int, val text: String)
@Entity(tableName = "exam_sessions")
data class ExamSessionEntity(@PrimaryKey val id: String, val startedAt: Long, val completedAt: Long, val total: Int, val correct: Int)
@Entity(tableName = "exam_answers", foreignKeys = [
    ForeignKey(entity = ExamSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("sessionId"), Index("questionId"), Index("categoryId")])
data class ExamAnswerEntity(@PrimaryKey val id: String, val sessionId: String, val questionId: String?, val categoryId: String?,
    val categoryPath: String, val question: String, val answer: String, val expected: String, val explanation: String,
    val correct: Boolean, val type: String)
@Entity(tableName = "exam_draft")
data class ExamDraftEntity(@PrimaryKey val slot: Int = 1, val json: String)
@Dao interface BankDao {
    @Query("SELECT * FROM categories") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM questions ORDER BY updatedAt DESC") fun questions(): Flow<List<QuestionEntity>>
    @Query("SELECT * FROM question_options ORDER BY position") fun options(): Flow<List<QuestionOptionEntity>>
    @Query("SELECT * FROM exam_sessions ORDER BY completedAt DESC") fun sessions(): Flow<List<ExamSessionEntity>>
    @Query("SELECT * FROM exam_answers") fun answers(): Flow<List<ExamAnswerEntity>>
    @Query("SELECT * FROM categories") suspend fun readCategories(): List<CategoryEntity>
    @Query("SELECT * FROM questions") suspend fun readQuestions(): List<QuestionEntity>
    @Query("SELECT * FROM question_options ORDER BY position") suspend fun readOptions(): List<QuestionOptionEntity>
    @Query("SELECT * FROM exam_sessions ORDER BY completedAt DESC") suspend fun readSessions(): List<ExamSessionEntity>
    @Query("SELECT * FROM exam_answers") suspend fun readAnswers(): List<ExamAnswerEntity>
    @Upsert suspend fun putCategory(category: CategoryEntity)
    @Upsert suspend fun putQuestion(question: QuestionEntity)
    @Insert suspend fun putOptions(options: List<QuestionOptionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putSession(session: ExamSessionEntity): Long
    @Insert suspend fun putAnswers(answers: List<ExamAnswerEntity>)
    @Query("DELETE FROM question_options WHERE questionId = :id") suspend fun deleteOptions(id: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM questions WHERE id = :id") suspend fun deleteQuestion(id: String)
    @Query("DELETE FROM exam_sessions") suspend fun clearSessions()
    @Query("DELETE FROM categories WHERE parentId IS NULL") suspend fun clearCategories()
    @Query("SELECT json FROM exam_draft WHERE slot = 1") suspend fun readDraft(): String?
    @Upsert suspend fun putDraft(draft: ExamDraftEntity)
    @Query("DELETE FROM exam_draft") suspend fun clearDraft()
}
@Database(entities = [CategoryEntity::class, QuestionEntity::class, QuestionOptionEntity::class,
    ExamSessionEntity::class, ExamAnswerEntity::class, ExamDraftEntity::class], version = 1, exportSchema = true)
abstract class BankDatabase : RoomDatabase() { abstract fun dao(): BankDao }
