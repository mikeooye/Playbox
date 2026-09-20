package com.playbox.games.ui.pinyin

import android.content.Context
import com.playbox.games.util.PinyinCardCounts
import com.playbox.games.util.PinyinDefaultCardCount

/** Remembers how many cards a pinyin round should have. */
internal class PinyinSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Int {
        val stored = preferences.getInt(KEY_CARD_COUNT, PinyinDefaultCardCount)
        return PinyinCardCounts.minByOrNull { kotlin.math.abs(it - stored) } ?: PinyinDefaultCardCount
    }

    fun save(cardCount: Int) {
        preferences.edit().putInt(KEY_CARD_COUNT, cardCount).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "pinyin-settings"
        const val KEY_CARD_COUNT = "cardCount"
    }
}
