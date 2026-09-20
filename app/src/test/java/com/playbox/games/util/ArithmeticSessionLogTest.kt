package com.playbox.games.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArithmeticSessionLogTest {
    private val problem = ArithmeticProblem(12, ArithmeticOperator.Add, 5, 17)

    @Test
    fun recordsTheTimeOfEverySuccessfulAnswer() {
        val log = ArithmeticSessionLog.Empty
            .record(0, problem, value = 17, correct = true, elapsedMillis = 2_400L, answeredAtMillis = 1_000L)
            .record(0, problem, value = 17, correct = true, elapsedMillis = 3_600L, answeredAtMillis = 9_000L)

        val record = requireNotNull(log.recordFor(0))
        assertEquals(2, record.correctCount)
        assertEquals(0, record.wrongCount)
        assertEquals(listOf(2_400L, 3_600L), record.correctTimesMillis)
        assertEquals(3_000L, record.averageCorrectMillis)
        assertEquals(2_400L, record.bestCorrectMillis)
        assertEquals(3_600L, record.lastCorrectMillis)
        assertEquals(9_000L, record.lastAnsweredAtMillis)
        assertEquals(6_000L, record.totalElapsedMillis)
    }

    @Test
    fun tracksWrongAnswersPerQuestion() {
        val log = ArithmeticSessionLog.Empty
            .record(2, problem, value = 7, correct = false, elapsedMillis = 1_000L, answeredAtMillis = 100L)
            .record(2, problem, value = 17, correct = true, elapsedMillis = 2_000L, answeredAtMillis = 200L)

        assertEquals(1, log.wrongCount(2))
        assertEquals(1, log.correctCount(2))
        assertTrue(requireNotNull(log.recordFor(2)).solved)
        assertFalse(requireNotNull(log.recordFor(2)).attempts.first().correct)
    }

    @Test
    fun keepsQuestionsSeparate() {
        val other = ArithmeticProblem(9, ArithmeticOperator.Subtract, 4, 5)
        val log = ArithmeticSessionLog.Empty
            .record(0, problem, value = 17, correct = true, elapsedMillis = 1_000L, answeredAtMillis = 0L)
            .record(5, other, value = 3, correct = false, elapsedMillis = 500L, answeredAtMillis = 0L)

        assertEquals(2, log.answeredCount)
        assertEquals(1, log.solvedCount)
        assertEquals(1, log.totalCorrect)
        assertEquals(1, log.totalWrong)
        assertEquals(1_500L, log.totalElapsedMillis)
        assertEquals(1_000L, log.totalCorrectMillis)
        assertEquals(0.5f, log.accuracy, 0.0001f)
        assertEquals(listOf(0, 5), log.records.map { it.index })
        assertNull(log.recordFor(1))
        assertEquals(0, log.wrongCount(1))
    }

    @Test
    fun tracksTheTimeUntilTheFirstCorrectAnswer() {
        val other = ArithmeticProblem(9, ArithmeticOperator.Subtract, 4, 5)
        val log = ArithmeticSessionLog.Empty
            // Missed once before it was solved: the first correct answer is what counts as the
            // time this question really took.
            .record(0, problem, value = 7, correct = false, elapsedMillis = 1_000L, answeredAtMillis = 0L)
            .record(0, problem, value = 17, correct = true, elapsedMillis = 4_000L, answeredAtMillis = 1L)
            .record(0, problem, value = 17, correct = true, elapsedMillis = 2_000L, answeredAtMillis = 2L)
            .record(1, other, value = 5, correct = true, elapsedMillis = 2_000L, answeredAtMillis = 3L)

        assertEquals(4_000L, requireNotNull(log.recordFor(0)).firstCorrectMillis)
        assertEquals(3_000L, log.averageFirstCorrectMillis)
    }

    @Test
    fun averageIsUnknownUntilSomethingWasAnswered() {
        val log = ArithmeticSessionLog.Empty
            .record(0, problem, value = 7, correct = false, elapsedMillis = 1_000L, answeredAtMillis = 0L)

        assertNull(log.averageFirstCorrectMillis)
        assertEquals(listOf(1_000L), log.records.map { it.totalElapsedMillis })
    }

    @Test
    fun emptyLogReportsNoActivity() {
        val log = ArithmeticSessionLog.Empty

        assertEquals(0, log.totalAttempts)
        assertEquals(0f, log.accuracy, 0.0001f)
        assertNull(log.averageCorrectMillis)
        assertTrue(log.records.isEmpty())
    }

    @Test
    fun formatsElapsedTimes() {
        assertEquals("800 毫秒", formatElapsed(800L))
        assertEquals("3.5 秒", formatElapsed(3_500L))
        assertEquals("1 分 05 秒", formatElapsed(65_000L))
    }
}
