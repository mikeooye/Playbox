package com.playbox.games.ui.dice

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.playbox.games.R
import com.playbox.games.ui.components.CircularPicker
import com.playbox.games.ui.components.PlayboxBackground
import com.playbox.games.ui.components.PlayboxScaffold
import com.playbox.games.ui.theme.PlayboxTokens
import com.playbox.games.util.Dice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DiceCameraHeight = 0f

@Composable
fun DiceScreen(onBack: (() -> Unit)?, compact: Boolean = false, onAddTool: (() -> Unit)? = null) {
    var value by remember { mutableIntStateOf(1) }
    var rolling by remember { mutableStateOf(false) }
    var rollSequence by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rollDice = {
        if (!rolling) {
            rolling = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            value = Dice.roll()
            rollSequence += 1
            scope.launch {
                delay(1500L)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                rolling = false
            }
        }
    }

    PlayboxBackground(dark = true) {
        PlayboxScaffold(
            title = "",
            subtitle = null,
            onBack = onBack,
            actions = {
                onAddTool?.let { onAdd -> TextButton(onClick = onAdd) { Text("＋ 添加") } }
            },
        ) { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxSize().padding(horizontal = PlayboxTokens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact) Spacer(Modifier.weight(1f))
                DiceGroup(value, compact, rolling = rolling, rollSequence = rollSequence, onClick = rollDice)
                Spacer(Modifier.weight(if (compact) .2f else 1f))
                Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
            }
        }
    }

}

@Composable
private fun DiceGroup(value: Int, compact: Boolean, rolling: Boolean, rollSequence: Int, onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val availableWidth = if (compact) {
        (configuration.screenWidthDp / 2 - 48).coerceAtLeast(120).dp
    } else {
        (configuration.screenWidthDp - 48).coerceAtLeast(160).dp
    }
    val availableHeight = (configuration.screenHeightDp - 88).coerceAtLeast(160).dp
    val diceSize = if (!compact) {
        configuration.screenWidthDp.dp
    } else {
        minOf(availableWidth, availableHeight)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.clickable(
            enabled = !rolling,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiceWheelViewport(
            value = value,
            size = diceSize,
            rolling = rolling,
            rollSequence = rollSequence,
        )
    }
}

@Composable
private fun DiceWheelViewport(
    value: Int,
    size: androidx.compose.ui.unit.Dp,
    rolling: Boolean,
    rollSequence: Int,
) {
    val faces = remember { (1..6).toList() }
    val itemSize = size * .92f
    CircularPicker(
        items = faces,
        targetIndex = value - 1,
        spinSequence = rollSequence,
        spinning = rolling,
        durationMillis = 1350,
        cameraHeight = DiceCameraHeight,
        viewportWidth = size,
        viewportHeight = size,
        itemWidth = itemSize,
        itemHeight = itemSize,
        minimumTurns = 1,
    ) { face, modifier ->
            Image(
                painter = painterResource(diceDrawable(face)),
                contentDescription = "$face 点",
                modifier = modifier,
            )
    }
}

@DrawableRes
private fun diceDrawable(value: Int): Int = when (value) {
    1 -> R.drawable.rabbit_dice_1
    2 -> R.drawable.rabbit_dice_2
    3 -> R.drawable.rabbit_dice_3
    4 -> R.drawable.rabbit_dice_4
    5 -> R.drawable.rabbit_dice_5
    else -> R.drawable.rabbit_dice_6
}
