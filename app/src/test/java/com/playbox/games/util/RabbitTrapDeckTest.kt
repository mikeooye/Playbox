package com.playbox.games.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class RabbitTrapDeckTest {
    @Test
    fun deckContainsSixCopiesOfEveryCard() {
        val deck = newRabbitTrapDeck(Random(7))

        assertEquals(RabbitTrapDeckSize, deck.size)
        RabbitTrapCard.entries.forEach { card ->
            assertEquals(RabbitTrapCopiesPerCard, deck.count { it == card })
        }
    }
}
