package com.playbox.games.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArithmeticDeckTest {
    @Test
    fun generatedRoundHasRequestedUniqueProblems() {
        val deck = newArithmeticDeck(50, Random(12))

        assertEquals(50, deck.size)
        assertEquals(deck.size, deck.distinct().size)
    }

    @Test
    fun everyProblemStaysWithinZeroAndTwenty() {
        assertTrue(arithmeticProblemBank.all { problem ->
            problem.left in 0..20 && problem.right in 0..20 && problem.answer in 0..20
        })
    }

    @Test
    fun additionDoesNotContainReversedDuplicates() {
        val additions = arithmeticProblemBank.filter { it.operator == ArithmeticOperator.Add }
        assertTrue(additions.all { it.left <= it.right })
    }

    @Test
    fun generatedRoundBalancesAdditionAndSubtraction() {
        val deck = newArithmeticDeck(20, Random(9))

        assertEquals(10, deck.count { it.operator == ArithmeticOperator.Add })
        assertEquals(10, deck.count { it.operator == ArithmeticOperator.Subtract })
    }

    @Test
    fun generatedProblemsRespectRangeAndSelectedOperators() {
        val operators = ArithmeticOperator.entries.toSet()
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(50, operators, Random(seed))
            assertTrue(problem.left in 0..50)
            assertTrue(problem.right in 0..50)
            assertTrue(problem.answer in 0..50)
            assertTrue(problem.operator in operators)
            assertEquals(problem.answer, evaluate(problem))
        }
    }

    @Test
    fun divisionAlwaysProducesAnIntegerResult() {
        repeat(200) { seed ->
            val problem = randomArithmeticProblem(100, setOf(ArithmeticOperator.Divide), Random(seed))
            assertTrue(problem.right > 0)
            assertEquals(0, problem.left % problem.right)
            assertEquals(problem.answer, problem.left / problem.right)
        }
    }

    @Test
    fun formulaDeckContainsRequestedNumberOfUniqueProblems() {
        val deck = newArithmeticFormulaDeck(
            count = 12,
            maxValue = 20,
            operators = setOf(ArithmeticOperator.Add, ArithmeticOperator.Subtract),
            random = Random(18),
        )

        assertEquals(12, deck.size)
        assertEquals(deck.size, deck.distinct().size)
    }

    private fun evaluate(problem: ArithmeticProblem): Int = when (problem.operator) {
        ArithmeticOperator.Add -> problem.left + problem.right
        ArithmeticOperator.Subtract -> problem.left - problem.right
        ArithmeticOperator.Multiply -> problem.left * problem.right
        ArithmeticOperator.Divide -> problem.left / problem.right
    }
}
