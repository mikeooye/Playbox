package com.playbox.games.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinDeckTest {
    @Test
    fun deckHasRequestedNumberOfDistinctSyllables() {
        val deck = newPinyinDeck(20, Random(7))

        assertEquals(20, deck.size)
        assertEquals(deck.size, deck.map { it.plain }.distinct().size)
        assertTrue(deck.all { it.character.isNotEmpty() && it.display.isNotEmpty() })
    }

    @Test
    fun deckNeverExceedsTheBank() {
        val deck = newPinyinDeck(999, Random(3))

        assertEquals(pinyinSyllableBank.size, deck.size)
    }

    @Test
    fun cardGrammarOnlyListensForThatSyllable() {
        val mother = pinyinSyllableBank.first { it.plain == "ma" }
        val grammar = pinyinCardGrammarJson(mother)

        assertTrue(grammar.contains("\"${mother.character}\""))
        assertTrue(grammar.contains("\"[unk]\""))
        // Nothing else may leak in, or a different syllable could be bent into this one.
        assertFalse(grammar.contains("八"))
    }

    @Test
    fun readingTheCardCounts() {
        val mother = pinyinSyllableBank.first { it.plain == "ma" }

        assertTrue(isCorrectReading(mother.character, mother))
        assertTrue(isCorrectReading(" ${mother.character} ", mother))
    }

    @Test
    fun anythingElseCountsAsAMiss() {
        val mother = pinyinSyllableBank.first { it.plain == "ma" }

        assertFalse(isCorrectReading("[unk]", mother))
        assertFalse(isCorrectReading("", mother))
        assertFalse(isCorrectReading("八", mother))
    }

    @Test
    fun toneIsNotPartOfTheSyllableIdentity() {
        // 妈 (mā) and 马 (mǎ) share the toneless syllable, so only the spelling decides a match.
        val mother = PinyinSyllable("ma", "mā", "妈")
        val horse = PinyinSyllable("ma", "mǎ", "马")

        assertEquals(mother.plain, horse.plain)
    }
}

class PinyinHandwritingTest {
    @Test
    fun plainLettersUseTheirHandwritingShapes() {
        assertEquals("bɑ", teachingForm("ba"))
        assertEquals("ɡe", teachingForm("ge"))
        assertEquals("zhɑn", teachingForm("zhan"))
    }

    @Test
    fun toneMarksStayAboveTheHandwrittenLetter() {
        // ɑ plus the combining macron/caron, never the printed ā/ǎ.
        assertEquals("mɑ\u0304", teachingForm("mā"))
        assertEquals("mɑ\u030C", teachingForm("mǎ"))
        assertEquals("hɑ\u030Co", teachingForm("hǎo"))
    }

    @Test
    fun onlyTheTwoHandwrittenLettersChange() {
        // Everything else keeps its printed shape; only a and g are swapped.
        assertEquals("zhōnɡ", teachingForm("zhōng"))
        assertEquals("xiě", teachingForm("xiě"))
        assertEquals("shuǐ", teachingForm("shuǐ"))
    }

    @Test
    fun everyCardUsesTheTeachingForm() {
        val deck = newPinyinDeck(40, Random(5))
        assertTrue(deck.none { it.teachingDisplay.contains('a') || it.teachingDisplay.contains('g') })
    }
}
