package com.playbox.games.ui.rabbittrap

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.playbox.games.R
import com.playbox.games.ui.components.CircularPicker
import com.playbox.games.ui.components.PlayboxBackground
import com.playbox.games.ui.components.PlayboxScaffold
import com.playbox.games.ui.theme.PlayboxTokens
import com.playbox.games.util.RabbitTrapCard
import com.playbox.games.util.newRabbitTrapDeck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CardSpinDurationMillis = 1_500L
private const val RabbitTrapCameraHeight = 0f

@Composable
fun RabbitTrapScreen(onBack: (() -> Unit)?, compact: Boolean = false, onAddTool: (() -> Unit)? = null) {
    val cards = remember { newRabbitTrapDeck() }
    var targetCard by remember { mutableStateOf(cards.first()) }
    var spinning by remember { mutableStateOf(false) }
    var spinSequence by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val compactCardHeight = minOf(270.dp, (configuration.screenHeightDp - 82).coerceAtLeast(160).dp)
    val cardAspectRatio = 728f / 1096f
    val regularCardWidth = configuration.screenWidthDp.dp * .8f
    val cardWidth = if (compact) {
        minOf(210.dp, (configuration.screenWidthDp / 2 - 48).coerceAtLeast(120).dp, compactCardHeight * .77f)
    } else {
        regularCardWidth
    }
    val cardHeight = if (compact) {
        compactCardHeight
    } else {
        regularCardWidth / cardAspectRatio
    }
    val spinCards: () -> Unit = {
        if (!spinning) {
            targetCard = cards.random()
            spinning = true
            spinSequence += 1
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                delay(CardSpinDurationMillis)
                spinning = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    PlayboxBackground(dark = true) {
        PlayboxScaffold(
            title = "",
            subtitle = null,
            onBack = onBack,
            actions = {
                onAddTool?.let { onAdd -> TextButton(enabled = !spinning, onClick = onAdd) { Text("＋ 添加") } }
            },
        ) { contentModifier ->
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(horizontal = PlayboxTokens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact) Spacer(Modifier.weight(.65f))
                CardDeckWheel(
                    cards = cards,
                    targetCard = targetCard,
                    spinning = spinning,
                    spinSequence = spinSequence,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    onSpin = spinCards,
                )
                Spacer(Modifier.weight(if (compact) .1f else 1f))
                Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
            }
        }
    }
}

@Composable
private fun CardDeckWheel(
    cards: List<RabbitTrapCard>,
    targetCard: RabbitTrapCard,
    spinning: Boolean,
    spinSequence: Int,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    onSpin: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .requiredSize(cardWidth + 16.dp, cardHeight + 18.dp)
            .clickable(
                enabled = !spinning,
                interactionSource = interactionSource,
                indication = null,
                onClick = onSpin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        RabbitCardWheelViewport(
            cards = cards,
            targetCard = targetCard,
            spinning = spinning,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            spinSequence = spinSequence,
        )
    }
}

@Composable
private fun RabbitCardWheelViewport(
    cards: List<RabbitTrapCard>,
    targetCard: RabbitTrapCard,
    spinning: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    spinSequence: Int,
) {
    val targetIndex = cards.indexOf(targetCard).coerceAtLeast(0)
    CircularPicker(
        items = cards,
        targetIndex = targetIndex,
        spinSequence = spinSequence,
        spinning = spinning,
        durationMillis = CardSpinDurationMillis.toInt(),
        cameraHeight = RabbitTrapCameraHeight,
        viewportWidth = cardWidth,
        viewportHeight = cardHeight,
        itemWidth = cardWidth,
        itemHeight = cardHeight,
    ) { card, modifier ->
        RabbitCardIllustration(card = card, modifier = modifier)
    }
}

@Composable
private fun RabbitCardIllustration(card: RabbitTrapCard, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(rabbitCardDrawable(card)),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

@DrawableRes
private fun rabbitCardDrawable(card: RabbitTrapCard): Int = when (card) {
    RabbitTrapCard.TurnCarrot -> R.drawable.rabbit_card_carrot
    RabbitTrapCard.MoveOne -> R.drawable.rabbit_card_step_1
    RabbitTrapCard.MoveTwo -> R.drawable.rabbit_card_step_2
    RabbitTrapCard.MoveThree -> R.drawable.rabbit_card_step_3
}
