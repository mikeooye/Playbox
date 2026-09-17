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

fun randomArithmeticProblem(
    maxValue: Int,
    operators: Set<ArithmeticOperator>,
    random: Random = Random.Default,
): ArithmeticProblem {
    require(maxValue > 0)
    require(operators.isNotEmpty())
    val operator = operators.random(random)
    return when (operator) {
        ArithmeticOperator.Add -> {
            val answer = random.nextInt(maxValue + 1)
            val left = random.nextInt(answer + 1)
            ArithmeticProblem(left, operator, answer - left, answer)
        }
        ArithmeticOperator.Subtract -> {
            val left = random.nextInt(maxValue + 1)
            val right = random.nextInt(left + 1)
            ArithmeticProblem(left, operator, right, left - right)
        }
        ArithmeticOperator.Multiply -> {
            val left = random.nextInt(1, maxValue + 1)
            val right = random.nextInt(maxValue / left + 1)
            ArithmeticProblem(left, operator, right, left * right)
        }
        ArithmeticOperator.Divide -> {
            val right = random.nextInt(1, maxValue + 1)
            val answer = random.nextInt(1, maxValue / right + 1)
            ArithmeticProblem(right * answer, operator, right, answer)
        }
    }
}

fun newArithmeticFormulaDeck(
    count: Int,
    maxValue: Int,
    operators: Set<ArithmeticOperator>,
    random: Random = Random.Default,
): List<ArithmeticProblem> {
    require(count > 0)
    val problems = linkedSetOf<ArithmeticProblem>()
    var attempts = 0
    val maxUniqueAttempts = count * 200
    while (problems.size < count && attempts < maxUniqueAttempts) {
        problems += randomArithmeticProblem(maxValue, operators, random)
        attempts += 1
    }
    val deck = problems.toMutableList()
    while (deck.size < count) {
        deck += randomArithmeticProblem(maxValue, operators, random)
    }
    return deck.shuffled(random)
}
