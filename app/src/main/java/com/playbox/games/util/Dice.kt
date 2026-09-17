package com.playbox.games.util

import kotlin.random.Random

object Dice {
    fun roll(random: Random = Random.Default): Int = random.nextInt(from = 1, until = 7)

    fun dotPositions(value: Int): List<Pair<Int, Int>> = when (value.coerceIn(1, 6)) {
        1 -> listOf(1 to 1)
        2 -> listOf(0 to 0, 2 to 2)
        3 -> listOf(0 to 0, 1 to 1, 2 to 2)
        4 -> listOf(0 to 0, 0 to 2, 2 to 0, 2 to 2)
        5 -> listOf(0 to 0, 0 to 2, 1 to 1, 2 to 0, 2 to 2)
        else -> listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2)
    }
}
