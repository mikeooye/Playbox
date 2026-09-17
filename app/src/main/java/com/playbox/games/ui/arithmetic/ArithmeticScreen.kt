package com.playbox.games.ui.arithmetic

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.ui.components.CircularPicker
import com.playbox.games.ui.components.PlayboxBackground
import com.playbox.games.ui.components.PlayboxScaffold
import com.playbox.games.ui.theme.PlayboxTokens
import com.playbox.games.util.ArithmeticOperator
import com.playbox.games.util.ArithmeticProblem
import com.playbox.games.util.newArithmeticFormulaDeck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ValueRanges = listOf(20, 50, 100)
private val CardCounts = (10..100 step 10).toList()
private val DefaultOperators = setOf(ArithmeticOperator.Add, ArithmeticOperator.Subtract)
private const val DefaultFormulaCardCount = 20
private const val ArithmeticAdvanceDurationMillis = 550L
private const val ArithmeticCameraHeight = 0f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArithmeticScreen(onBack: (() -> Unit)?, compact: Boolean = false) {
    var valueRange by remember { mutableIntStateOf(20) }
    var formulaCardCount by remember { mutableIntStateOf(DefaultFormulaCardCount) }
    var selectedOperators by remember { mutableStateOf(DefaultOperators) }
    var formulas by remember {
        mutableStateOf(newArithmeticFormulaDeck(formulaCardCount, valueRange, selectedOperators))
    }
    var targetIndex by remember { mutableIntStateOf(0) }
    var advancing by remember { mutableStateOf(false) }
    var stepDirection by remember { mutableIntStateOf(1) }
    var answerRevealed by remember { mutableStateOf(false) }
    var spinSequence by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val cardWidth = if (compact) {
        minOf(210.dp, (configuration.screenWidthDp / 2 - 48).coerceAtLeast(120).dp)
    } else {
        configuration.screenWidthDp.dp * .8f
    }
    val cardHeight = if (compact) {
        minOf(270.dp, (configuration.screenHeightDp - 82).coerceAtLeast(160).dp)
    } else {
        minOf(configuration.screenHeightDp.dp * .92f, cardWidth * 2.05f)
    }
    val refreshFormulas: (Int, Set<ArithmeticOperator>, Int) -> Unit = { range, operators, count ->
        formulas = newArithmeticFormulaDeck(count, range, operators)
        targetIndex = 0
        answerRevealed = false
    }
    val navigate: (Int) -> Unit = { step ->
        if (!advancing && formulas.isNotEmpty()) {
            val nextIndex = (targetIndex + step).coerceIn(formulas.indices)
            if (nextIndex != targetIndex) {
                answerRevealed = false
                stepDirection = step
                targetIndex = nextIndex
                advancing = true
                spinSequence += 1
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    delay(ArithmeticAdvanceDurationMillis)
                    advancing = false
                }
            }
        }
    }

    PlayboxBackground(dark = true) {
        PlayboxScaffold(
            title = "",
            onBack = onBack,
            actions = {
                TextButton(enabled = !advancing, onClick = { showSettings = true }) { Text("设置") }
                Text(
                    text = "${targetIndex + 1}/${formulas.size}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
        ) { contentModifier ->
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(horizontal = PlayboxTokens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                FormulaWheel(
                    formulas = formulas,
                    targetIndex = targetIndex,
                    spinSequence = spinSequence,
                    advancing = advancing,
                    stepDirection = stepDirection,
                    answerRevealed = answerRevealed,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    compact = compact,
                    onNext = { navigate(1) },
                    onPrevious = { navigate(-1) },
                    onRevealAnswer = { answerRevealed = true },
                    onHideAnswer = { answerRevealed = false },
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
            }
        }
    }

    if (showSettings) {
        ArithmeticSettings(
            valueRange = valueRange,
            formulaCardCount = formulaCardCount,
            selectedOperators = selectedOperators,
            onDismiss = { showSettings = false },
            onRangeChange = { range ->
                valueRange = range
                refreshFormulas(range, selectedOperators, formulaCardCount)
            },
            onOperatorsChange = { operators ->
                selectedOperators = operators
                refreshFormulas(valueRange, operators, formulaCardCount)
            },
            onCardCountChange = { count ->
                formulaCardCount = count
                refreshFormulas(valueRange, selectedOperators, count)
            },
        )
    }
}

@Composable
private fun FormulaWheel(
    formulas: List<ArithmeticProblem>,
    targetIndex: Int,
    spinSequence: Int,
    advancing: Boolean,
    stepDirection: Int,
    answerRevealed: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRevealAnswer: () -> Unit,
    onHideAnswer: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardOffset by animateDpAsState(
        targetValue = if (answerRevealed) 0.dp else 170.dp,
        label = "answerRevealOffset",
    )
    val formulaOffset by animateDpAsState(
        targetValue = if (answerRevealed) cardHeight * -.01f else cardHeight * .08f,
        label = "formulaRevealOffset",
    )
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    CircularPicker(
        items = formulas,
        targetIndex = targetIndex,
        spinSequence = spinSequence,
        spinning = advancing,
        durationMillis = ArithmeticAdvanceDurationMillis.toInt(),
        cameraHeight = ArithmeticCameraHeight,
        viewportWidth = cardWidth,
        viewportHeight = cardHeight,
        itemWidth = cardWidth,
        itemHeight = cardHeight,
        minimumTurns = 0,
        spinStepDelta = stepDirection,
        modifier = Modifier
            .offset(y = cardOffset)
            .pointerInput(advancing) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        when {
                            abs(dragX) > abs(dragY) && dragX > 60f -> onPrevious()
                            abs(dragX) > abs(dragY) && dragX < -60f -> onNext()
                            abs(dragY) > abs(dragX) && dragY < -60f -> onRevealAnswer()
                            abs(dragY) > abs(dragX) && dragY > 60f -> onHideAnswer()
                        }
                    },
                ) { change, dragAmount ->
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                    change.consume()
                }
            }
            .clickable(
                enabled = !advancing,
                interactionSource = interactionSource,
                indication = null,
                onClick = onNext,
            ),
    ) { formula, modifier ->
        val formulaFontSize = if (compact) 40.sp else 82.sp
        val formulaWidth = cardHeight * .94f
        val symbolSlotWidth = if (compact) 34.dp else 52.dp
        val numberSlotWidth = (formulaWidth - symbolSlotWidth * 2) / 3
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(if (compact) 24.dp else 34.dp),
            color = Color.White,
            shadowElevation = 10.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .requiredWidth(formulaWidth)
                        .height(if (compact) 58.dp else 112.dp)
                        .offset(y = formulaOffset)
                        .graphicsLayer { rotationZ = 90f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormulaSlot(formula.left.toString(), numberSlotWidth, formulaFontSize)
                    FormulaSlot(formula.operator.symbol, symbolSlotWidth, formulaFontSize)
                    FormulaSlot(formula.right.toString(), numberSlotWidth, formulaFontSize)
                    FormulaSlot("=", symbolSlotWidth, formulaFontSize)
                    FormulaSlot(formula.answer.toString(), numberSlotWidth, formulaFontSize)
                }
            }
        }
    }
}

@Composable
private fun FormulaSlot(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArithmeticSettings(
    valueRange: Int,
    formulaCardCount: Int,
    selectedOperators: Set<ArithmeticOperator>,
    onDismiss: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onOperatorsChange: (Set<ArithmeticOperator>) -> Unit,
    onCardCountChange: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text("算术转盘设置", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(18.dp))
            Text("数值范围", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueRanges.forEach { range ->
                    SettingChoice(
                        text = range.toString(),
                        selected = range == valueRange,
                        modifier = Modifier.weight(1f),
                        onClick = { onRangeChange(range) },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("运算符（至少选择一个）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArithmeticOperator.entries.forEach { operator ->
                    SettingChoice(
                        text = operator.symbol,
                        selected = operator in selectedOperators,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val nextOperators = if (operator in selectedOperators) {
                                if (selectedOperators.size == 1) selectedOperators else selectedOperators - operator
                            } else {
                                selectedOperators + operator
                            }
                            onOperatorsChange(nextOperators)
                        },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("卡牌数量", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            CardCounts.chunked(5).forEachIndexed { index, counts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.forEach { count ->
                        SettingChoice(
                            text = count.toString(),
                            selected = count == formulaCardCount,
                            modifier = Modifier.weight(1f),
                            onClick = { onCardCountChange(count) },
                        )
                    }
                }
                if (index < CardCounts.lastIndex / 5) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(46.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}
