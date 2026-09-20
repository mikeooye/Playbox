package com.playbox.games.util

/**
 * Turns a spoken or typed answer into a number.
 *
 * Speech recognition for Chinese either returns Arabic digits ("15") or Chinese numerals
 * ("十五"), and it often wraps the number in filler words ("答案是十五", "等于 15"). The parser
 * accepts both shapes, tolerates surrounding text, and returns `null` when nothing numeric
 * can be recognised so the caller can simply ignore that utterance.
 */
object SpokenNumberParser {

    private val digitValues: Map<Char, Int> = buildMap {
        "零〇○洞".forEach { put(it, 0) }
        "一壹幺".forEach { put(it, 1) }
        "二贰两".forEach { put(it, 2) }
        "三叁".forEach { put(it, 3) }
        "四肆".forEach { put(it, 4) }
        "五伍".forEach { put(it, 5) }
        "六陆".forEach { put(it, 6) }
        "七柒".forEach { put(it, 7) }
        "八捌".forEach { put(it, 8) }
        "九玖".forEach { put(it, 9) }
        for (digit in 0..9) put('0' + digit, digit)
    }

    private val unitValues: Map<Char, Int> = buildMap {
        "十拾".forEach { put(it, 10) }
        "百佰".forEach { put(it, 100) }
        "千仟".forEach { put(it, 1000) }
    }

    private val minusChars = charArrayOf('负', '−', '-', '－')
    private val explicitZeroChars = charArrayOf('零', '〇', '○')

    /** Returns the answer contained in [text], or `null` when no number can be read out of it. */
    fun parse(text: String?): Int? {
        val normalized = normalize(text) ?: return null
        val negative = normalized.any { it in minusChars }
        if (negative) {
            // Only the number itself is meaningful; the sign never belongs to the digits.
            return parseMagnitude(normalized)?.let { -it }
        }
        return parseMagnitude(normalized)
    }

    /**
     * Tries every recognised alternative in order and returns the first one that parses. The
     * speech recogniser returns its best guess first, which is normally the right one.
     */
    fun parseFirst(candidates: List<String>): Int? {
        for (candidate in candidates) {
            parse(candidate)?.let { return it }
        }
        return null
    }

    private fun parseMagnitude(text: String): Int? {
        val runs = numeralRuns(text)
        if (runs.isEmpty()) return null
        // The spoken answer usually trails the sentence ("十二加三等于十五"), so the last
        // readable number is the answer.
        for (run in runs.asReversed()) {
            parseRun(run)?.let { return it }
        }
        return null
    }

    private fun normalize(text: String?): String? {
        if (text == null) return null
        val builder = StringBuilder(text.length)
        for (char in text) {
            when (char) {
                in '０'..'９' -> builder.append('0' + (char - '０'))
                '＋' -> builder.append('+')
                '＝' -> builder.append('=')
                // Chinese recognisers often separate words with spaces ("十 五"), which must not
                // break the number apart.
                ' ', '\t', '\n', '\u3000' -> Unit
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    /** Splits the text into maximal stretches made only of numeral characters. */
    private fun numeralRuns(text: String): List<String> {
        val runs = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            if (isNumeralChar(char)) {
                current.append(char)
            } else if (current.isNotEmpty()) {
                runs += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) runs += current.toString()
        return runs
    }

    private fun isNumeralChar(char: Char): Boolean = char in digitValues || char in unitValues || char == '.'

    private fun parseRun(run: String): Int? {
        val trimmed = run.trim('.')
        if (trimmed.isEmpty()) return null
        // "十五" uses units while "一五" is read digit by digit, so the two shapes differ.
        return if (trimmed.any { it in unitValues }) parseUnitRun(trimmed) else parseDigitRun(trimmed)
    }

    private fun parseDigitRun(run: String): Int? {
        var value = 0L
        for (char in run) {
            val digit = digitValues[char] ?: return null
            value = value * 10 + digit
            if (value > Int.MAX_VALUE) return null
        }
        return value.toInt()
    }

    private fun parseUnitRun(run: String): Int? {
        var total = 0
        var pending: Int? = null
        var lastUnit = 0
        for (char in run) {
            val digit = digitValues[char]
            if (digit != null) {
                pending = digit
                continue
            }
            val unit = unitValues[char] ?: return null
            total += (pending ?: 1) * unit
            lastUnit = unit
            pending = null
        }
        val trailing = pending
        if (trailing != null) {
            // Colloquial shorthand: "一百二" means 120, not 102. An explicit 零 keeps it literal.
            val hasExplicitZero = run.any { it in explicitZeroChars }
            total += if (lastUnit > 10 && !hasExplicitZero) trailing * (lastUnit / 10) else trailing
        }
        return total
    }
}
