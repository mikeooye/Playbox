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
            val problem = randomArithmeticProblem(0, 50, operators, Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 0..50)
            assertTrue("left=${problem.left}", problem.left in 0..50)
            assertTrue("right=${problem.right}", problem.right in 0..50)
            assertTrue(problem.operator in operators)
            assertEquals(problem.answer, evaluate(problem))
        }
    }

    /** The range describes the answer; no number in the formula may be larger than its maximum. */
    @Test
    fun answersStayInsideAnOffsetRange() {
        val operators = ArithmeticOperator.entries.toSet()
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(1, 20, operators, Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 1..20)
            assertTrue("left=${problem.left}", problem.left in 0..20)
            assertTrue("right=${problem.right}", problem.right in 0..20)
            assertEquals(problem.answer, evaluate(problem))
        }
    }

    @Test
    fun tenToTwentyNeverExceedsTwenty() {
        val operators = ArithmeticOperator.entries.toSet()
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(10, 20, operators, Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 10..20)
            assertTrue("left=${problem.left}", problem.left <= 20)
            assertTrue("right=${problem.right}", problem.right <= 20)
        }
    }

    @Test
    fun subtractionNeverGoesNegative() {
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Subtract), Random(seed))
            assertTrue(problem.left >= problem.right)
            assertTrue(problem.answer in 1..20)
            assertTrue(problem.left <= 20)
        }
    }

    @Test
    fun multiplicationStaysInsideTheRange() {
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Multiply), Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 1..20)
            assertTrue(problem.left <= 20)
            assertTrue(problem.right <= 20)
            assertEquals(problem.answer, problem.left * problem.right)
        }
    }

    @Test
    fun divisionAlwaysProducesAnIntegerResultInsideTheRange() {
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(10, 100, setOf(ArithmeticOperator.Divide), Random(seed))
            assertTrue(problem.right > 0)
            assertTrue("answer=${problem.answer}", problem.answer in 10..100)
            assertTrue("dividend=${problem.left}", problem.left in 0..100)
            assertEquals(0, problem.left % problem.right)
            assertEquals(problem.answer, problem.left / problem.right)
        }
    }

    @Test
    fun divisionStillWorksWhenTheRangeStartsAtZero() {
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(0, 100, setOf(ArithmeticOperator.Divide), Random(seed))
            assertTrue(problem.right > 0)
            assertTrue(problem.answer in 0..100)
            assertEquals(0, problem.left % problem.right)
            assertEquals(problem.answer, problem.left / problem.right)
        }
    }

    @Test
    fun formulaDeckContainsRequestedNumberOfUniqueProblems() {
        val deck = newArithmeticFormulaDeck(
            count = 12,
            minValue = 0,
            maxValue = 20,
            operators = setOf(ArithmeticOperator.Add, ArithmeticOperator.Subtract),
            random = Random(18),
        )

        assertEquals(12, deck.size)
        assertEquals(deck.size, deck.distinct().size)
    }

    @Test
    fun formulaDeckKeepsAnswersInsideTheConfiguredRange() {
        val deck = newArithmeticFormulaDeck(
            count = 20,
            minValue = 10,
            maxValue = 20,
            operators = setOf(ArithmeticOperator.Add, ArithmeticOperator.Subtract),
            random = Random(24),
        )

        assertEquals(20, deck.size)
        assertTrue(deck.all { it.answer in 10..20 })
        assertTrue(deck.all { it.left <= 20 && it.right <= 20 })
    }

    @Test
    fun rangeHelperSnapsBoundsIntoValidLimits() {
        assertEquals(ArithmeticRange(0, 1), arithmeticRange(-5, 0))
        assertEquals(ArithmeticRange(0, 100), arithmeticRange(0, 100))
        assertEquals(ArithmeticRange(99, 100), arithmeticRange(120, 120))
    }

    private fun evaluate(problem: ArithmeticProblem): Int = when (problem.operator) {
        ArithmeticOperator.Add -> problem.left + problem.right
        ArithmeticOperator.Subtract -> problem.left - problem.right
        ArithmeticOperator.Multiply -> problem.left * problem.right
        ArithmeticOperator.Divide -> problem.left / problem.right
    }
}
