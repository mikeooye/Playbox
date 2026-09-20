package com.playbox.games.util

import kotlin.random.Random

enum class ArithmeticOperator(val symbol: String) {
    Add("+"),
    Subtract("−"),
    Multiply("×"),
    Divide("÷"),
}

data class ArithmeticProblem(
    val left: Int,
    val operator: ArithmeticOperator,
    val right: Int,
    val answer: Int,
) {
    val expression: String get() = "$left ${operator.symbol} $right"
}

/**
 * The numeric range a problem is generated from: the **answer** always lands inside
 * [min]..[max], and no number used by the formula is larger than [max].
 *
 * For example 1~20 yields 13 + 7 = 20 and 20 − 8 = 12, but never 18 + 17 = 35 and never a factor
 * larger than 20.
 */
data class ArithmeticRange(val min: Int, val max: Int) {
    init {
        require(min >= 0) { "range minimum must not be negative" }
        require(max > min) { "range maximum must be greater than the minimum" }
    }

    val label: String get() = "$min~$max"

    companion object {
        val Default = ArithmeticRange(min = 1, max = 20)
    }
}

/** Largest value a range may use, kept in step with what the settings sheet offers. */
const val ArithmeticMaxRangeValue = 100

/** Smallest step used when the settings sheet nudges a range bound. */
const val ArithmeticRangeStep = 5

/** Snaps an arbitrary pair of bounds into a valid, in-limit [ArithmeticRange]. */
fun arithmeticRange(min: Int, max: Int): ArithmeticRange {
    val safeMin = min.coerceIn(0, ArithmeticMaxRangeValue - 1)
    val safeMax = max.coerceIn(safeMin + 1, ArithmeticMaxRangeValue)
    return ArithmeticRange(safeMin, safeMax)
}

/**
 * Builds one problem whose answer sits inside [minValue]..[maxValue] while every number used by
 * the formula stays at or below [maxValue].
 *
 * Every operator is exact by construction: subtraction never goes below zero and division always
 * divides evenly, so the answer is always a whole number inside the requested range.
 *
 * Each operator also skips the questions a child learns nothing from, by drawing its answer from
 * the window where the formula can avoid a degenerate operand:
 *
 * - `+` splits the answer into two non-zero addends, so `0 + n` only survives when the answer is
 *   too small to split (0 or 1).
 * - `−` keeps the subtrahend at one or more, which rules out `n − 0` entirely; the price is that
 *   the very top of the range is reached through addition instead.
 * - `×` draws the answer from the values that factorise into two numbers greater than one, so
 *   `1 × n` only survives on ranges with no such value at all (1~3, for example).
 * - `÷` prefers a divisor of two or more, so `n ÷ 1` only appears when the range has no room for
 *   a larger one (for example 15~20, where the answer is at least 15 but the dividend may not
 *   pass 20).
 */
fun randomArithmeticProblem(
    minValue: Int,
    maxValue: Int,
    operators: Set<ArithmeticOperator>,
    random: Random = Random.Default,
): ArithmeticProblem {
    val range = ArithmeticRange(minValue, maxValue)
    require(operators.isNotEmpty()) { "at least one operator is required" }
    val operator = operators.random(random)
    return when (operator) {
        ArithmeticOperator.Add -> {
            // left + right = answer, so neither addend can exceed the answer.
            val answer = random.nextInt(range.min, range.max + 1)
            val left = if (answer >= 2) random.nextInt(1, answer) else random.nextInt(0, answer + 1)
            ArithmeticProblem(left, operator, answer - left, answer)
        }
        ArithmeticOperator.Subtract -> {
            // left - right = answer, with left kept inside the range maximum. Asking for a
            // subtrahend of at least one needs an answer below the maximum, which always exists
            // because a range requires max > min.
            val answer = random.nextInt(range.min, range.max)
            val right = random.nextInt(1, range.max - answer + 1)
            ArithmeticProblem(answer + right, operator, right, answer)
        }
        ArithmeticOperator.Multiply -> {
            // Answers that factorise into two numbers greater than one, so the question is never
            // just "n × 1". When the range holds no such answer (1~3, say), every split is trivial
            // and the plain range is used instead.
            val factorable = (range.min..range.max).filter { answer ->
                answer >= 4 && (2..answer / 2).any { factor -> answer % factor == 0 }
            }
            val answer = if (factorable.isEmpty()) {
                random.nextInt(range.min, range.max + 1)
            } else {
                factorable.random(random)
            }
            if (answer == 0) {
                ArithmeticProblem(0, operator, nontrivialFactor(range.max, random), 0)
            } else {
                val factors = (2..answer / 2).filter { answer % it == 0 }
                val left = if (factors.isEmpty()) answer else factors.random(random)
                ArithmeticProblem(left, operator, answer / left, answer)
            }
        }
        ArithmeticOperator.Divide -> {
            // Staying at or below half the range leaves room for a divisor of at least two.
            val ceiling = maxOf(range.min, range.max / 2)
            val answer = random.nextInt(range.min, ceiling + 1)
            if (answer == 0) {
                ArithmeticProblem(0, operator, nontrivialFactor(range.max, random), 0)
            } else {
                // dividend = answer * divisor, which stays inside the range maximum.
                val maxDivisor = range.max / answer
                val divisor = if (maxDivisor >= 2) random.nextInt(2, maxDivisor + 1) else 1
                ArithmeticProblem(answer * divisor, operator, divisor, answer)
            }
        }
    }
}

/** Two or more when the range has room for it, so `0 × n` / `0 ÷ n` avoid a trivial operand. */
private fun nontrivialFactor(maxValue: Int, random: Random): Int =
    if (maxValue >= 2) random.nextInt(2, maxValue + 1) else 1

fun newArithmeticFormulaDeck(
    count: Int,
    minValue: Int,
    maxValue: Int,
    operators: Set<ArithmeticOperator>,
    random: Random = Random.Default,
): List<ArithmeticProblem> {
    require(count > 0)
    val problems = linkedSetOf<ArithmeticProblem>()
    var attempts = 0
    val maxUniqueAttempts = count * 200
    while (problems.size < count && attempts < maxUniqueAttempts) {
        problems += randomArithmeticProblem(minValue, maxValue, operators, random)
        attempts += 1
    }
    val deck = problems.toMutableList()
    while (deck.size < count) {
        deck += randomArithmeticProblem(minValue, maxValue, operators, random)
    }
    return deck.shuffled(random)
}
