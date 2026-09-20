package com.playbox.games.ui.arithmetic

import android.content.Context
import com.playbox.games.util.ArithmeticOperator
import com.playbox.games.util.ArithmeticRange
import com.playbox.games.util.arithmeticRange

/** Everything the arithmetic tool remembers between launches. */
internal data class ArithmeticSettings(
    val range: ArithmeticRange,
    val cardCount: Int,
    val operators: Set<ArithmeticOperator>,
    val dictationEnabled: Boolean,
) {
    companion object {
        const val DefaultCardCount = 20
        val AllowedCardCounts = (10..100 step 10).toList()
        val DefaultOperators = setOf(ArithmeticOperator.Add, ArithmeticOperator.Subtract)

        val Default = ArithmeticSettings(
            range = ArithmeticRange.Default,
            cardCount = DefaultCardCount,
            operators = DefaultOperators,
            dictationEnabled = true,
        )
    }
}

/**
 * Persists the arithmetic settings in [android.content.SharedPreferences].
 *
 * Reads are defensive: anything missing or unreadable falls back to the defaults, and a stored
 * value that is no longer offered (an old card count, an unknown operator) is snapped back into
 * the supported set instead of producing a deck the settings sheet cannot represent.
 */
internal class ArithmeticSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ArithmeticSettings {
        if (!preferences.contains(KEY_RANGE_MAX)) return ArithmeticSettings.Default
        return runCatching {
            val range = arithmeticRange(
                preferences.getInt(KEY_RANGE_MIN, ArithmeticRange.Default.min),
                preferences.getInt(KEY_RANGE_MAX, ArithmeticRange.Default.max),
            )
            val operators = preferences.getStringSet(KEY_OPERATORS, null)
                ?.mapNotNull { name -> ArithmeticOperator.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: ArithmeticSettings.DefaultOperators
            ArithmeticSettings(
                range = range,
                cardCount = nearestCardCount(preferences.getInt(KEY_CARD_COUNT, ArithmeticSettings.DefaultCardCount)),
                operators = operators,
                dictationEnabled = preferences.getBoolean(KEY_DICTATION, true),
            )
        }.getOrDefault(ArithmeticSettings.Default)
    }

    fun save(settings: ArithmeticSettings) {
        preferences.edit()
            .putInt(KEY_RANGE_MIN, settings.range.min)
            .putInt(KEY_RANGE_MAX, settings.range.max)
            .putInt(KEY_CARD_COUNT, settings.cardCount)
            .putStringSet(KEY_OPERATORS, settings.operators.map { it.name }.toSet())
            .putBoolean(KEY_DICTATION, settings.dictationEnabled)
            .apply()
    }

    private fun nearestCardCount(value: Int): Int = ArithmeticSettings.AllowedCardCounts
        .minByOrNull { candidate -> kotlin.math.abs(candidate - value) }
        ?: ArithmeticSettings.DefaultCardCount

    private companion object {
        const val PREFERENCES_NAME = "arithmetic-settings"
        const val KEY_RANGE_MIN = "rangeMin"
        const val KEY_RANGE_MAX = "rangeMax"
        const val KEY_CARD_COUNT = "cardCount"
        const val KEY_OPERATORS = "operators"
        const val KEY_DICTATION = "dictationEnabled"
    }
}
