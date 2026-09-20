package com.playbox.games.util

/**
 * What follows a finished pass over the deck.
 *
 * A round takes two passes at most: the main pass asks every question once, then the questions
 * that were never answered correctly come back for exactly one more attempt. Nothing gets a third
 * chance, so the retry pass always ends the round.
 */
sealed interface PassOutcome {
    /** These deck indices were never answered correctly and get their second attempt. */
    data class Retry(val indices: List<Int>) : PassOutcome

    /** The round is over and the report is shown. */
    data object Finished : PassOutcome
}

/**
 * Decides what the end of a pass leads to.
 *
 * [failedIndices] are the questions that were never answered correctly, in deck order, and
 * [isRetryPass] says whether the pass that just ended was itself the retry pass.
 *
 * The decision deliberately looks at nothing else — in particular not at how the pass ended. A
 * correct answer to the last card has to reach it exactly like a wrong one does: finishing the
 * round from inside the answer handler is what used to swallow the retry pass whenever the child
 * got the final card right.
 */
fun nextPass(failedIndices: List<Int>, isRetryPass: Boolean): PassOutcome =
    if (!isRetryPass && failedIndices.isNotEmpty()) PassOutcome.Retry(failedIndices) else PassOutcome.Finished
