package com.playbox.games.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.playbox.games.ui.dice.DiceScreen
import com.playbox.games.ui.arithmetic.ArithmeticScreen
import com.playbox.games.ui.pinyin.PinyinScreen
import com.playbox.games.ui.dual.AddToolSheet
import com.playbox.games.ui.dual.DualToolHost
import com.playbox.games.ui.home.HomeScreen
import com.playbox.games.ui.rabbittrap.RabbitTrapScreen

private enum class Destination { Home, Dice, RabbitTrap, Arithmetic, Pinyin, Dual }

@Composable
fun PlayboxApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var addingTo by remember { mutableStateOf<ToolKind?>(null) }
    var dualPair by remember { mutableStateOf<Pair<ToolKind, ToolKind>?>(null) }
    BackHandler(enabled = destination != Destination.Home) {
        addingTo = null
        destination = Destination.Home
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val entering = if (targetState == Destination.Home) -1 else 1
            (slideInHorizontally(tween(320)) { entering * it / 4 } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(tween(260)) { -entering * it / 4 } + fadeOut(tween(180)))
        },
        label = "page transition",
    ) { page ->
        when (page) {
            Destination.Home -> HomeScreen(
                onOpenDice = { destination = Destination.Dice },
                onOpenRabbitTrap = { destination = Destination.RabbitTrap },
                onOpenArithmetic = { destination = Destination.Arithmetic },
                onOpenPinyin = { destination = Destination.Pinyin },
            )
            Destination.Dice -> DiceScreen(
                onBack = { destination = Destination.Home },
                onAddTool = { addingTo = ToolKind.Dice },
            )
            Destination.RabbitTrap -> RabbitTrapScreen(
                onBack = { destination = Destination.Home },
                onAddTool = { addingTo = ToolKind.RabbitTrap },
            )
            Destination.Arithmetic -> ArithmeticScreen(
                onBack = { destination = Destination.Home },
            )
            Destination.Pinyin -> PinyinScreen(
                onBack = { destination = Destination.Home },
            )
            Destination.Dual -> dualPair?.let { pair ->
                DualToolHost(left = pair.first, right = pair.second, onBack = { destination = Destination.Home })
            }
        }
    }

    addingTo?.let { current ->
        AddToolSheet(
            current = current,
            onDismiss = { addingTo = null },
            onSelect = { selected ->
                dualPair = current to selected
                addingTo = null
                destination = Destination.Dual
            },
        )
    }
}
