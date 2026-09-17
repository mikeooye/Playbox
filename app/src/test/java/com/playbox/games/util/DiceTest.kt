package com.playbox.games.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceTest {
    @Test fun everyFaceHasMatchingDotCount() {
        (1..6).forEach { assertEquals(it, Dice.dotPositions(it).size) }
    }

    @Test fun rollsStayWithinDiceRange() {
        repeat(1_000) { assertTrue(Dice.roll(Random(it)) in 1..6) }
    }
}
