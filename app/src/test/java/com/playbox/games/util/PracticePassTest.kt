package com.playbox.games.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round progression for both practice tools: every question is asked once in the main pass, the
 * ones that were never answered correctly come back once, and the round then ends.
 */
class PracticePassTest {
    @Test
    fun aMainPassWithMissedQuestionsStartsTheRetryPass() {
        val missed = listOf(0, 4, 9)

        assertEquals(PassOutcome.Retry(missed), nextPass(missed, isRetryPass = false))
    }

    /** Nothing to retry means the round is over as soon as the main pass ends. */
    @Test
    fun aCleanMainPassEndsTheRound() {
        assertEquals(PassOutcome.Finished, nextPass(emptyList(), isRetryPass = false))
    }

    /** The retry pass is the last chance: missing the same question again ends the round. */
    @Test
    fun theRetryPassNeverGetsAThirdChance() {
        assertEquals(PassOutcome.Finished, nextPass(listOf(2, 5), isRetryPass = true))
    }

    /**
     * The bug this decision was extracted for: when the last card of the main pass was answered
     * correctly, the screen finished the round on the spot and this function was never consulted,
     * so earlier mistakes silently lost their retry. The decision itself only looks at the missed
     * questions — not at how the pass ended — which is what keeps that from happening again.
     */
    @Test
    fun missedQuestionsSurviveAMainPassThatEndedOnACorrectAnswer() {
        val missedEarly = listOf(1, 6)

        assertEquals(PassOutcome.Retry(missedEarly), nextPass(missedEarly, isRetryPass = false))
    }

    /** The retry asks the missed questions in deck order, which is the order the log is keyed by. */
    @Test
    fun theRetryKeepsTheOrderOfTheMissedQuestions() {
        val missed = listOf(3, 7, 11, 12)

        val outcome = nextPass(missed, isRetryPass = false)

        assertEquals(listOf(3, 7, 11, 12), (outcome as PassOutcome.Retry).indices)
    }

    /** A full deck missed in the main pass is still retried as a whole. */
    @Test
    fun everyQuestionCanComeBackOnce() {
        val all = (0 until 20).toList()

        assertEquals(PassOutcome.Retry(all), nextPass(all, isRetryPass = false))
        assertEquals(PassOutcome.Finished, nextPass(all, isRetryPass = true))
    }
}
