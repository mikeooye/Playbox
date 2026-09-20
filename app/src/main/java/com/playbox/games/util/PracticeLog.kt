package com.playbox.games.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One judged attempt at a prompt: what was heard, whether it was right, and how long it took. */
data class PracticeAttempt(
    val heard: String,
    val correct: Boolean,
    val elapsedMillis: Long,
    val answeredAtMillis: Long,
)

/** Everything recorded for one card of a practice round. */
data class PracticeQuestionRecord(
    val index: Int,
    val prompt: String,
    val answer: String,
    val attempts: List<PracticeAttempt> = emptyList(),
) {
    val correctCount: Int get() = attempts.count { it.correct }
    val wrongCount: Int get() = attempts.count { !it.correct }
    val answered: Boolean get() = attempts.isNotEmpty()
    val solved: Boolean get() = correctCount > 0
    val totalElapsedMillis: Long get() = attempts.sumOf { it.elapsedMillis }
    val correctTimesMillis: List<Long> get() = attempts.filter { it.correct }.map { it.elapsedMillis }

    /** How long the card took until it was answered correctly the first time. */
    val firstCorrectMillis: Long? get() = correctTimesMillis.firstOrNull()
    val lastCorrectMillis: Long? get() = correctTimesMillis.lastOrNull()
    val bestCorrectMillis: Long? get() = correctTimesMillis.minOrNull()

    /** Average time across the attempts that were right, slower ones included. */
    val averageCorrectMillis: Long?
        get() = correctTimesMillis.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
    val lastAnsweredAtMillis: Long? get() = attempts.lastOrNull()?.answeredAtMillis
}

/**
 * Immutable log of one practice round, shared by every practice tool: the arithmetic deck answers
 * with a number, the pinyin reader with a syllable, and both record the same shape of attempt.
 * Every answer folds into a new instance so it can drive Compose state directly.
 */
data class PracticeSessionLog(
    val recordsByIndex: Map<Int, PracticeQuestionRecord> = emptyMap(),
) {
    val records: List<PracticeQuestionRecord> get() = recordsByIndex.values.sortedBy { it.index }

    fun recordFor(index: Int): PracticeQuestionRecord? = recordsByIndex[index]

    fun wrongCount(index: Int): Int = recordFor(index)?.wrongCount ?: 0

    fun correctCount(index: Int): Int = recordFor(index)?.correctCount ?: 0

    fun record(
        index: Int,
        prompt: String,
        answer: String,
        heard: String,
        correct: Boolean,
        elapsedMillis: Long,
        answeredAtMillis: Long,
    ): PracticeSessionLog {
        val previous = recordsByIndex[index]
        val attempts = previous?.attempts.orEmpty() +
            PracticeAttempt(
                heard = heard,
                correct = correct,
                elapsedMillis = elapsedMillis.coerceAtLeast(0L),
                answeredAtMillis = answeredAtMillis,
            )
        val updated = PracticeQuestionRecord(
            index = index,
            prompt = prompt,
            answer = answer,
            attempts = attempts,
        )
        return copy(recordsByIndex = recordsByIndex + (index to updated))
    }

    val answeredCount: Int get() = records.count { it.answered }
    val solvedCount: Int get() = records.count { it.solved }
    val totalCorrect: Int get() = records.sumOf { it.correctCount }
    val totalWrong: Int get() = records.sumOf { it.wrongCount }
    val totalAttempts: Int get() = totalCorrect + totalWrong
    val totalElapsedMillis: Long get() = records.sumOf { it.totalElapsedMillis }

    /** Time spent on answers that turned out to be right. */
    val totalCorrectMillis: Long get() = records.sumOf { record -> record.correctTimesMillis.sum() }

    val accuracy: Float
        get() = if (totalAttempts == 0) 0f else totalCorrect.toFloat() / totalAttempts

    /** Average time across every successful answer, slower repeats included. */
    val averageCorrectMillis: Long?
        get() = records.flatMap { it.correctTimesMillis }
            .takeIf { it.isNotEmpty() }
            ?.let { times -> times.sum() / times.size }

    /**
     * Average time a question took to be answered correctly. Every question counts once, so a
     * question repeated later does not skew the reference used to flag slow answers.
     */
    val averageFirstCorrectMillis: Long?
        get() = records.mapNotNull { it.firstCorrectMillis }
            .takeIf { it.isNotEmpty() }
            ?.let { times -> times.sum() / times.size }

    companion object {
        val Empty = PracticeSessionLog()
    }
}

/** "3.4 秒" / "1 分 05 秒", with a plain millisecond fallback below one second. */
fun formatElapsed(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    if (safeMillis < 1_000L) return "$safeMillis 毫秒"
    val totalSeconds = safeMillis / 1_000.0
    if (totalSeconds < 60.0) return String.format(Locale.US, "%.1f 秒", totalSeconds)
    val minutes = safeMillis / 60_000L
    val seconds = (safeMillis % 60_000L) / 1_000L
    return String.format(Locale.US, "%d 分 %02d 秒", minutes, seconds)
}

/** Wall-clock stamp of an answer, used in the per-question history. */
fun formatClockTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
