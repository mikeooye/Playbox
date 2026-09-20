package com.playbox.games.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenNumberParserTest {
    @Test
    fun readsArabicDigits() {
        assertEquals(0, SpokenNumberParser.parse("0"))
        assertEquals(7, SpokenNumberParser.parse("7"))
        assertEquals(15, SpokenNumberParser.parse("15"))
        assertEquals(120, SpokenNumberParser.parse("120"))
    }

    @Test
    fun readsFullWidthDigits() {
        assertEquals(15, SpokenNumberParser.parse("１５"))
    }

    @Test
    fun readsSimpleChineseDigits() {
        assertEquals(1, SpokenNumberParser.parse("一"))
        assertEquals(2, SpokenNumberParser.parse("两"))
        assertEquals(2, SpokenNumberParser.parse("二"))
        assertEquals(9, SpokenNumberParser.parse("九"))
        assertEquals(5, SpokenNumberParser.parse("伍"))
    }

    @Test
    fun readsChineseTens() {
        assertEquals(10, SpokenNumberParser.parse("十"))
        assertEquals(11, SpokenNumberParser.parse("十一"))
        assertEquals(15, SpokenNumberParser.parse("十五"))
        assertEquals(20, SpokenNumberParser.parse("二十"))
        assertEquals(23, SpokenNumberParser.parse("二十三"))
        assertEquals(99, SpokenNumberParser.parse("九十九"))
    }

    @Test
    fun readsChineseHundreds() {
        assertEquals(100, SpokenNumberParser.parse("一百"))
        assertEquals(102, SpokenNumberParser.parse("一百零二"))
        assertEquals(110, SpokenNumberParser.parse("一百一十"))
        assertEquals(123, SpokenNumberParser.parse("一百二十三"))
        // Colloquial shorthand drops the final unit: 一百二 means 120.
        assertEquals(120, SpokenNumberParser.parse("一百二"))
        assertEquals(250, SpokenNumberParser.parse("二百五"))
    }

    @Test
    fun readsDigitsSpokenOneByOne() {
        assertEquals(15, SpokenNumberParser.parse("一五"))
        assertEquals(123, SpokenNumberParser.parse("一二三"))
    }

    @Test
    fun ignoresSurroundingWords() {
        assertEquals(15, SpokenNumberParser.parse("十五。"))
        assertEquals(15, SpokenNumberParser.parse("答案是十五"))
        assertEquals(15, SpokenNumberParser.parse("等于 15"))
        assertEquals(15, SpokenNumberParser.parse("15 对吗"))
    }

    @Test
    fun joinsWordsTheRecogniserSeparatedWithSpaces() {
        // The offline recogniser returns Chinese words separated by spaces.
        assertEquals(15, SpokenNumberParser.parse("十 五"))
        assertEquals(23, SpokenNumberParser.parse("二 十 三"))
        assertEquals(100, SpokenNumberParser.parse("一 百"))
        assertEquals(7, SpokenNumberParser.parse(" 七 "))
    }

    @Test
    fun prefersTheTrailingAnswerInAFullSentence() {
        assertEquals(15, SpokenNumberParser.parse("十二加三等于十五"))
    }

    @Test
    fun rejectsTextWithoutANumber() {
        assertNull(SpokenNumberParser.parse(null))
        assertNull(SpokenNumberParser.parse(""))
        assertNull(SpokenNumberParser.parse("不知道"))
        assertNull(SpokenNumberParser.parse("apple"))
    }

    @Test
    fun understandsNegativeAnswers() {
        assertEquals(-3, SpokenNumberParser.parse("负三"))
        assertEquals(-12, SpokenNumberParser.parse("-12"))
    }

    @Test
    fun picksTheFirstAlternativeTheRecogniserOffers() {
        assertEquals(17, SpokenNumberParser.parseFirst(listOf("十七", "是七")))
        assertEquals(17, SpokenNumberParser.parseFirst(listOf("um", "十七")))
        assertNull(SpokenNumberParser.parseFirst(listOf("不知道", "嗯")))
    }
}
