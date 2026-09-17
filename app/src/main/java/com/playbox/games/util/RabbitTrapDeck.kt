package com.playbox.games.util

import kotlin.random.Random

enum class RabbitTrapCard(val title: String, val shortLabel: String) {
    TurnCarrot("转动萝卜", "转萝卜"),
    MoveOne("兔子走一步", "1 步"),
    MoveTwo("兔子走两步", "2 步"),
    MoveThree("兔子走三步", "3 步"),
}

const val RabbitTrapCopiesPerCard = 2
const val RabbitTrapDeckSize = 8

fun newRabbitTrapDeck(random: Random = Random.Default): List<RabbitTrapCard> =
    RabbitTrapCard.entries
        .flatMap { card -> List(RabbitTrapCopiesPerCard) { card } }
        .shuffled(random)
