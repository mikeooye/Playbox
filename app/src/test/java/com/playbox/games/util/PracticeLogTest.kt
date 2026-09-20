package com.playbox.games.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one practice log both tools record into: the arithmetic deck answers with a number, the
 * pinyin reader with a syllable, and neither of them needs its own copy of this bookkeeping.
 */
class PracticeLogTest {
    private val prompt = "12 + 5"
    private val answer = "17"

    @Test
    fun recordsTheTimeOfEverySuccessfulAnswer() {
        val log = PracticeSessionLog.Empty
            .answer(index = 0, heard = "17", correct = true, elapsedMillis = 2_400L, answeredAtMillis = 1_000L)
            .answer(index = 0, heard = "17", correct = true, elapsedMillis = 3_600L, answeredAtMillis = 9_000L)

        val record = requireNotNull(log.recordFor(0))
        assertEquals(prompt, record.prompt)
        assertEquals(answer, record.answer)
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
        val log = PracticeSessionLog.Empty
            .answer(index = 2, heard = "7", correct = false, elapsedMillis = 1_000L, answeredAtMillis = 100L)
            .answer(index = 2, heard = "17", correct = true, elapsedMillis = 2_000L, answeredAtMillis = 200L)

        assertEquals(1, log.wrongCount(2))
        assertEquals(1, log.correctCount(2))
        assertTrue(requireNotNull(log.recordFor(2)).solved)
        assertFalse(requireNotNull(log.recordFor(2)).attempts.first().correct)
        assertEquals("7", requireNotNull(log.recordFor(2)).attempts.first().heard)
    }

    @Test
    fun keepsQuestionsSeparate() {
        val log = PracticeSessionLog.Empty
            .answer(index = 0, heard = "17", correct = true, elapsedMillis = 1_000L, answeredAtMillis = 0L)
            .answer(index = 5, heard = "3", correct = false, elapsedMillis = 500L, answeredAtMillis = 0L)

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
        val log = PracticeSessionLog.Empty
            // Missed once before it was solved: the first correct answer is what counts as the
            // time this question really took.
            .answer(index = 0, heard = "7", correct = false, elapsedMillis = 1_000L, answeredAtMillis = 0L)
            .answer(index = 0, heard = "17", correct = true, elapsedMillis = 4_000L, answeredAtMillis = 1L)
            .answer(index = 0, heard = "17", correct = true, elapsedMillis = 2_000L, answeredAtMillis = 2L)
            .answer(index = 1, heard = "5", correct = true, elapsedMillis = 2_000L, answeredAtMillis = 3L)

        assertEquals(4_000L, requireNotNull(log.recordFor(0)).firstCorrectMillis)
        assertEquals(3_000L, log.averageFirstCorrectMillis)
        assertEquals(2_666L, requireNotNull(log.averageCorrectMillis))
    }

    @Test
    fun averageIsUnknownUntilSomethingWasAnswered() {
        val log = PracticeSessionLog.Empty
            .answer(index = 0, heard = "7", correct = false, elapsedMillis = 1_000L, answeredAtMillis = 0L)

        assertNull(log.averageFirstCorrectMillis)
        assertNull(log.averageCorrectMillis)
        assertEquals(listOf(1_000L), log.records.map { it.totalElapsedMillis })
    }

    @Test
    fun emptyLogReportsNoActivity() {
        val log = PracticeSessionLog.Empty

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
        // A negative duration can only come from a clock jump; it is shown as zero, not as "-".
        assertEquals("0 毫秒", formatElapsed(-5L))
    }

    @Test
    fun formatsWallClockStamps() {
        assertTrue(formatClockTime(1_700_000_000_000L).matches(Regex("""\d{2}:\d{2}:\d{2}""")))
    }

    /** Records one attempt at [index] with the prompts this test file shares. */
    private fun PracticeSessionLog.answer(
        index: Int,
        heard: String,
        correct: Boolean,
        elapsedMillis: Long,
        answeredAtMillis: Long,
    ): PracticeSessionLog = record(
        index = index,
        prompt = prompt,
        answer = answer,
        heard = heard,
        correct = correct,
        elapsedMillis = elapsedMillis,
        answeredAtMillis = answeredAtMillis,
    )
}
