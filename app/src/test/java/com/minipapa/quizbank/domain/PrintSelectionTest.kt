package com.minipapa.quizbank.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PrintSelectionTest {
    private val categories = listOf(Category("a", "업무"), Category("child", "IT", "a"), Category("b", "기타"))
    private fun question(id: String, category: String = "a", type: QuestionType = QuestionType.SHORT) =
        Question(id, category, type, "문제 $id", if (type == QuestionType.MULTIPLE) listOf("보기 A", "보기 B") else emptyList(),
            if (type == QuestionType.MULTIPLE) "1" else if (type == QuestionType.OX) "O" else "정답 $id", createdAt = id.toLongOrNull() ?: 0)

    @Test fun filtersSupportDescendantsMultipleCategoriesAndLegacyTypes() {
        val bank = Bank(categories, listOf(question("1"), question("2", "child", QuestionType.MULTIPLE), question("3", "b", QuestionType.OX)))
        val options = PrintOptions(categoryIds = setOf("a"))
        assertEquals(listOf("1", "2"), PrintSelector.candidates(bank, options).map { it.id })
        assertEquals(listOf("1"), PrintSelector.candidates(bank, options.copy(includeDescendants = false)).map { it.id })
        assertEquals(3, PrintSelector.candidates(bank, options.copy(categoryIds = setOf("a", "b"))).size)
        assertEquals("2", PrintSelector.candidates(bank, options.copy(types = setOf(QuestionType.MULTIPLE))).single().id)
    }

    @Test fun randomSelectionIsUniqueAndFrozenForBothDocuments() {
        val questions = (1..60).map { question("$it") }
        val bank = Bank(categories, questions + questions.first())
        val options = PrintOptions(count = 50, randomOrder = true)
        val selection = PrintSelector.select(bank, options, Random(42))
        assertEquals(50, selection.questions.size)
        assertEquals(50, selection.questions.map { it.id }.distinct().size)
        assertEquals(selection, PrintSelector.select(bank, options, Random(42)))
        assertNotEquals(questions.take(50), selection.questions)
        assertEquals(bank.questions, questions + questions.first())
    }

    @Test fun registrationOrderAndCountHandleSmallBanksAndIncompleteQuestions() {
        val valid = listOf(question("3"), question("1"), question("2"))
        val bank = Bank(categories, valid + question("bad").copy(content = "") + question("invalid", type = QuestionType.MULTIPLE).copy(answer = "50"))
        assertEquals(listOf("1", "2", "3"), PrintSelector.select(bank, PrintOptions(count = 10)).questions.map { it.id })
        assertEquals(3, PrintSelector.select(bank, PrintOptions(count = 0)).questions.size)
    }

    @Test fun emptyConditionsFailWithActionableMessage() {
        val error = runCatching { PrintSelector.select(Bank(), PrintOptions()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("출력할 문제가 없습니다"))
    }
}
