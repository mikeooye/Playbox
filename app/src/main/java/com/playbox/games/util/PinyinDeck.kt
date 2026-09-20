package com.playbox.games.util

import kotlin.random.Random

/**
 * One syllable to read aloud.
 *
 * [plain] is the toneless spelling and is what a spoken answer is compared against: the bundled
 * recogniser cannot tell 妈 from 麻 from 马 reliably, so tones are shown but never judged.
 * [character] is both the example on the card and the word the recogniser is asked to listen for.
 */
data class PinyinSyllable(
    val plain: String,
    val display: String,
    val character: String,
) {
    val label: String get() = "$teachingDisplay  $character"

    /**
     * The card spells the syllable the way it is written by hand.
     *
     * Pinyin is taught with the single storey ɑ and ɡ, while printed fonts draw the double storey
     * a and g — a difference that matters when a child is still learning the letters.
     */
    val teachingDisplay: String get() = teachingForm(display)
}

/** Tone mark that belongs over each toned a, as a combining character. */
private val combiningToneMarks = mapOf(
    'ā' to '\u0304',
    'á' to '\u0301',
    'ǎ' to '\u030C',
    'à' to '\u0300',
)

/**
 * Rewrites printed letters as their handwriting counterparts: a becomes ɑ and g becomes ɡ, with
 * any tone mark kept above the letter.
 */
fun teachingForm(text: String): String = buildString(text.length) {
    text.forEach { char ->
        val tone = combiningToneMarks[char]
        when {
            tone != null -> append('ɑ').append(tone)
            char == 'a' -> append('ɑ')
            char == 'g' -> append('ɡ')
            else -> append(char)
        }
    }
}

/**
 * The syllable pool the pinyin tool draws from. Characters are deliberately common ones, because
 * a character the speech model does not know simply cannot be recognised.
 */
val pinyinSyllableBank: List<PinyinSyllable> = listOf(
    PinyinSyllable("a", "ā", "啊"),
    PinyinSyllable("o", "ō", "哦"),
    PinyinSyllable("e", "é", "鹅"),
    PinyinSyllable("yi", "yī", "衣"),
    PinyinSyllable("wu", "wǔ", "五"),
    PinyinSyllable("yu", "yú", "鱼"),
    PinyinSyllable("ba", "bā", "八"),
    PinyinSyllable("bo", "bō", "波"),
    PinyinSyllable("bi", "bǐ", "比"),
    PinyinSyllable("bu", "bù", "不"),
    PinyinSyllable("pa", "pà", "怕"),
    PinyinSyllable("pi", "pí", "皮"),
    PinyinSyllable("po", "pó", "婆"),
    PinyinSyllable("ma", "mā", "妈"),
    PinyinSyllable("mo", "mō", "摸"),
    PinyinSyllable("mi", "mǐ", "米"),
    PinyinSyllable("mu", "mù", "木"),
    PinyinSyllable("fa", "fā", "发"),
    PinyinSyllable("fu", "fù", "父"),
    PinyinSyllable("da", "dà", "大"),
    PinyinSyllable("de", "dé", "得"),
    PinyinSyllable("di", "dì", "弟"),
    PinyinSyllable("du", "dú", "读"),
    PinyinSyllable("ta", "tā", "他"),
    PinyinSyllable("ti", "tī", "踢"),
    PinyinSyllable("tu", "tù", "兔"),
    PinyinSyllable("na", "ná", "拿"),
    PinyinSyllable("ni", "nǐ", "你"),
    PinyinSyllable("la", "lā", "拉"),
    PinyinSyllable("le", "le", "了"),
    PinyinSyllable("li", "lǐ", "里"),
    PinyinSyllable("lu", "lù", "路"),
    PinyinSyllable("ge", "gē", "哥"),
    PinyinSyllable("gu", "gǔ", "古"),
    PinyinSyllable("ka", "kǎ", "卡"),
    PinyinSyllable("ke", "kě", "可"),
    PinyinSyllable("ku", "kǔ", "苦"),
    PinyinSyllable("ha", "hā", "哈"),
    PinyinSyllable("he", "hē", "喝"),
    PinyinSyllable("hu", "hǔ", "虎"),
    PinyinSyllable("ji", "jī", "鸡"),
    PinyinSyllable("ju", "jǔ", "举"),
    PinyinSyllable("qi", "qī", "七"),
    PinyinSyllable("qu", "qù", "去"),
    PinyinSyllable("xi", "xī", "西"),
    PinyinSyllable("xu", "xǔ", "许"),
    PinyinSyllable("zhi", "zhī", "知"),
    PinyinSyllable("chi", "chī", "吃"),
    PinyinSyllable("shi", "shì", "是"),
    PinyinSyllable("ri", "rì", "日"),
    PinyinSyllable("zi", "zì", "字"),
    PinyinSyllable("ci", "cí", "词"),
    PinyinSyllable("si", "sì", "四"),
    PinyinSyllable("zhe", "zhè", "这"),
    PinyinSyllable("che", "chē", "车"),
    PinyinSyllable("she", "shé", "蛇"),
    PinyinSyllable("re", "rè", "热"),
    PinyinSyllable("ce", "cè", "册"),
    PinyinSyllable("se", "sè", "色"),
    PinyinSyllable("ya", "yā", "鸭"),
    PinyinSyllable("ye", "yè", "叶"),
    PinyinSyllable("yao", "yào", "药"),
    PinyinSyllable("you", "yǒu", "有"),
    PinyinSyllable("yan", "yǎn", "眼"),
    PinyinSyllable("yin", "yīn", "音"),
    PinyinSyllable("yang", "yáng", "羊"),
    PinyinSyllable("wo", "wǒ", "我"),
    PinyinSyllable("wen", "wén", "文"),
    PinyinSyllable("wang", "wáng", "王"),
    PinyinSyllable("an", "ān", "安"),
    PinyinSyllable("ai", "ài", "爱"),
    PinyinSyllable("ou", "ǒu", "藕"),
    PinyinSyllable("en", "ēn", "恩"),
    PinyinSyllable("ang", "áng", "昂"),
    PinyinSyllable("han", "hàn", "汉"),
    PinyinSyllable("hao", "hǎo", "好"),
    PinyinSyllable("shan", "shān", "山"),
    PinyinSyllable("zhong", "zhōng", "中"),
    PinyinSyllable("guo", "guó", "国"),
    PinyinSyllable("xue", "xué", "学"),
    PinyinSyllable("shui", "shuǐ", "水"),
    PinyinSyllable("hua", "huā", "花"),
    PinyinSyllable("tian", "tiān", "天"),
    PinyinSyllable("xing", "xīng", "星"),
    PinyinSyllable("peng", "péng", "朋"),
    PinyinSyllable("lai", "lái", "来"),
)

const val PinyinDefaultCardCount = 20

val PinyinCardCounts = (10..40 step 10).toList()

/** A round of syllables: no repeats, shuffled, and never longer than the bank. */
fun newPinyinDeck(
    count: Int,
    random: Random = Random.Default,
): List<PinyinSyllable> {
    require(count > 0)
    val size = count.coerceAtMost(pinyinSyllableBank.size)
    return pinyinSyllableBank.shuffled(random).take(size)
}

/**
 * The words the recogniser listens for while one card is on screen.
 *
 * Narrowing the vocabulary to the expected syllable turns recognition into verification, which the
 * bundled model handles far better: with the whole round's syllables in play, common pairs such as
 * 八/啊 and 踢/皮 were regularly confused, while a single-syllable vocabulary read back correctly
 * for every one of the 86 syllables in the bank.
 */
fun pinyinCardGrammarJson(syllable: PinyinSyllable): String =
    "[\"${syllable.character}\",\"$UnknownWord\"]"

const val UnknownWord = "[unk]"

/**
 * True when the recogniser heard the syllable on the card. Anything else — a different reading, a
 * cough, the child saying nothing — comes back as [unk] and counts as a miss.
 */
fun isCorrectReading(heard: String, syllable: PinyinSyllable): Boolean {
    // Unknown pieces are dropped rather than treated as failures: a cough or the pause in a drawn
    // out syllable must not stop a reading that does arrive.
    val cleaned = heard.replace(UnknownWord, "").filterNot { it.isWhitespace() }
    return cleaned.isNotEmpty() && cleaned.first() == syllable.character.first()
}
