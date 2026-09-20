package com.playbox.games.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArithmeticDeckTest {
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

    /** `n − 0` teaches nothing, so the subtrahend is always a real number. */
    @Test
    fun subtractionNeverAsksForAZeroSubtrahend() {
        for (range in listOf(1 to 20, 10 to 20, 0 to 50)) {
            repeat(500) { seed ->
                val problem = randomArithmeticProblem(
                    range.first,
                    range.second,
                    setOf(ArithmeticOperator.Subtract),
                    Random(seed),
                )
                assertTrue("$range right=${problem.right}", problem.right >= 1)
                assertTrue(problem.answer in range.first..range.second)
                assertTrue(problem.left <= range.second)
            }
        }
    }

    @Test
    fun additionUsesTwoNonZeroAddendsWheneverTheAnswerAllowsIt() {
        repeat(1000) { seed ->
            val problem = randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Add), Random(seed))
            if (problem.answer >= 2) {
                assertTrue("${problem.left}+${problem.right}", problem.left >= 1 && problem.right >= 1)
            }
            assertEquals(problem.answer, problem.left + problem.right)
        }
    }

    /** Restricting the answer must not shrink the deck to a corner of the range. */
    @Test
    fun additionStillReachesBothEndsOfTheRange() {
        val answers = (0 until 1000).map { seed ->
            randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Add), Random(seed)).answer
        }.toSet()

        assertEquals((1..20).toSet(), answers)
    }

    /** `n × 1` is not worth a card, so only answers that really factorise are drawn. */
    @Test
    fun multiplicationNeverUsesOneAsAFactorWhenTheRangeHasAFactorableAnswer() {
        repeat(1000) { seed ->
            val problem = randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Multiply), Random(seed))
            assertTrue("${problem.left}×${problem.right}", problem.left >= 2 && problem.right >= 2)
            assertEquals(problem.answer, problem.left * problem.right)
        }
    }

    /** A range with nothing to factorise (1~3) still has to yield a correct, in-range problem. */
    @Test
    fun multiplicationFallsBackOnARangeWithoutFactorableAnswers() {
        repeat(300) { seed ->
            val problem = randomArithmeticProblem(1, 3, setOf(ArithmeticOperator.Multiply), Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 1..3)
            assertTrue(problem.left <= 3 && problem.right <= 3)
            assertEquals(problem.answer, problem.left * problem.right)
        }
    }

    @Test
    fun divisionAvoidsOneAsADivisorWhenTheRangeAllowsIt() {
        repeat(1000) { seed ->
            val problem = randomArithmeticProblem(1, 20, setOf(ArithmeticOperator.Divide), Random(seed))
            assertTrue("divisor=${problem.right}", problem.right >= 2)
            assertEquals(0, problem.left % problem.right)
            assertEquals(problem.answer, problem.left / problem.right)
        }
    }

    /** A narrow high range cannot avoid ÷ 1, but it must stay exact and inside the range. */
    @Test
    fun divisionStaysExactOnANarrowHighRange() {
        repeat(500) { seed ->
            val problem = randomArithmeticProblem(15, 20, setOf(ArithmeticOperator.Divide), Random(seed))
            assertTrue("answer=${problem.answer}", problem.answer in 15..20)
            assertTrue("dividend=${problem.left}", problem.left in 15..20)
            assertTrue(problem.right >= 1)
            assertEquals(0, problem.left % problem.right)
            assertEquals(problem.answer, problem.left / problem.right)
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
