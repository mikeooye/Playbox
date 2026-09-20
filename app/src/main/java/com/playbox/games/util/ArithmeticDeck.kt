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
 * The fixed 0~20 problem bank behind [newArithmeticDeck]. The interactive deck is built by
 * [newArithmeticFormulaDeck], which can honour any configured range and operator mix.
 */
val arithmeticProblemBank: List<ArithmeticProblem> = buildList {
    for (left in 0..20) {
        for (right in left..(20 - left)) {
            add(ArithmeticProblem(left, ArithmeticOperator.Add, right, left + right))
        }
    }
    for (left in 0..20) {
        for (right in 0..left) {
            add(ArithmeticProblem(left, ArithmeticOperator.Subtract, right, left - right))
        }
    }
}

fun newArithmeticDeck(count: Int, random: Random = Random.Default): List<ArithmeticProblem> {
    require(count in 1..arithmeticProblemBank.size)
    val additionCount = (count + 1) / 2
    val subtractionCount = count / 2
    val additions = arithmeticProblemBank.filter { it.operator == ArithmeticOperator.Add }.shuffled(random).take(additionCount)
    val subtractions = arithmeticProblemBank.filter { it.operator == ArithmeticOperator.Subtract }.shuffled(random).take(subtractionCount)
    return (additions + subtractions).shuffled(random)
}

/**
 * Builds one problem whose answer sits inside [minValue]..[maxValue] while every number used by
 * the formula stays at or below [maxValue].
 *
 * Every operator is exact by construction: subtraction never goes below zero and division always
 * divides evenly, so the answer is always a whole number inside the requested range.
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
    val answer = random.nextInt(range.min, range.max + 1)
    return when (operator) {
        ArithmeticOperator.Add -> {
            // left + right = answer, so neither addend can exceed the answer.
            val left = random.nextInt(0, answer + 1)
            ArithmeticProblem(left, operator, answer - left, answer)
        }
        ArithmeticOperator.Subtract -> {
            // left - right = answer, with left kept inside the range maximum.
            val right = random.nextInt(0, range.max - answer + 1)
            ArithmeticProblem(answer + right, operator, right, answer)
        }
        ArithmeticOperator.Multiply -> {
            if (answer == 0) {
                ArithmeticProblem(0, operator, random.nextInt(0, range.max + 1), 0)
            } else {
                val factors = (1..answer).filter { answer % it == 0 }
                val left = factors.random(random)
                ArithmeticProblem(left, operator, answer / left, answer)
            }
        }
        ArithmeticOperator.Divide -> {
            if (answer == 0) {
                ArithmeticProblem(0, operator, random.nextInt(1, range.max + 1), 0)
            } else {
                // dividend = answer * divisor, which stays inside the range maximum.
                val divisor = random.nextInt(1, range.max / answer + 1)
                ArithmeticProblem(answer * divisor, operator, divisor, answer)
            }
        }
    }
}

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
