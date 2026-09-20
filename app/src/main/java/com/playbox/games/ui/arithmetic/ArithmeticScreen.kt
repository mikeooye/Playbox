package com.playbox.games.ui.arithmetic

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.ui.components.AboveAverageAccent
import com.playbox.games.ui.components.AdvanceDelayMillis
import com.playbox.games.ui.components.CardCorrectColor
import com.playbox.games.ui.components.CardFirstWrongColor
import com.playbox.games.ui.components.CardNeutralColor
import com.playbox.games.ui.components.CardSecondWrongColor
import com.playbox.games.ui.components.CorrectAccent
import com.playbox.games.ui.components.FinishSummary
import com.playbox.games.ui.components.MicToggle
import com.playbox.games.ui.components.PlayboxBackground
import com.playbox.games.ui.components.PlayboxScaffold
import com.playbox.games.ui.components.RoundActionButton
import com.playbox.games.ui.components.RoundHint
import com.playbox.games.ui.components.StackedCardDeck
import com.playbox.games.ui.components.WrongAccent
import com.playbox.games.ui.dictation.DictationPhase
import com.playbox.games.ui.dictation.hasRecordPermission
import com.playbox.games.ui.dictation.rememberDictationEngine
import com.playbox.games.ui.theme.PlayboxTokens
import com.playbox.games.util.ArithmeticOperator
import com.playbox.games.util.ArithmeticProblem
import com.playbox.games.util.PracticeQuestionRecord
import com.playbox.games.util.ArithmeticRange
import com.playbox.games.util.ArithmeticRangeStep
import com.playbox.games.util.PassOutcome
import com.playbox.games.util.PracticeSessionLog
import com.playbox.games.util.SpokenNumberParser
import com.playbox.games.util.arithmeticRange
import com.playbox.games.util.formatClockTime
import com.playbox.games.util.formatElapsed
import com.playbox.games.util.newArithmeticFormulaDeck
import com.playbox.games.util.nextPass
import kotlinx.coroutines.delay

/** Result ranges offered as one-tap presets; the bounds themselves stay adjustable. */
private val RangePresets = listOf(
    ArithmeticRange(0, 5),
    ArithmeticRange(0, 10),
    ArithmeticRange(1, 20),
    ArithmeticRange(10, 20),
    ArithmeticRange(0, 50),
    ArithmeticRange(0, 100),
)

private val CardCounts = ArithmeticSettings.AllowedCardCounts

/** A card turns red on the second miss; the child then taps it to move on. */
private const val MaxWrongAttempts = 2

/** Guards against the recogniser re-delivering the very same utterance twice. */
private const val RepeatedUtteranceGuardMillis = 600L

/**
 * A drawn out number ("十……五") reaches the recogniser as two separate utterances, because the
 * pause in the middle reads as the end of speech. A miss is therefore held for this long: if the
 * rest of the number arrives, the two pieces are judged together.
 */
private const val UtteranceMergeWindowMillis = 900L

/** Height-to-width ratio of a formula card. */
private const val CardAspectRatio = 2.05f

// Everything drawn inside a card is a fraction of the card's width, so the same layout works on
// a phone and on a tablet, where the card — and therefore the type — is far larger.
private const val CardCornerRatio = .12f
private const val FormulaFontRatio = .29f
private const val FormulaFontRatioCompact = .19f
private const val SymbolSlotRatio = .18f
private const val SymbolSlotRatioCompact = .16f
private const val FormulaRowThicknessRatio = .40f
private const val FormulaRowThicknessRatioCompact = .28f

// How the deck is stacked: the cards behind the top one sit only a hair lower and a hair
// smaller, so a thin edge is all that shows.
private const val StackGapRatio = .010f
private const val StackScaleStep = .018f

/** Screen height kept free for the top bar and the margins around the deck. */
private val CardLayoutReserve = 190.dp

/** Smallest gap kept between the status line and the bottom of the screen. */
private val BottomEdgeGuard = 46.dp

private val PausedAccent = Color(0xFFFFB74D)
private val GuessAccent = Color(0xFF5B67D8)

/** Where a practice round is: not started, waking the recogniser up, running, or done. */
private enum class RoundPhase { Idle, Preparing, Running, Finished }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArithmeticScreen(onBack: (() -> Unit)?, compact: Boolean = false) {
    val context = LocalContext.current
    val settingsStore = remember(context) { ArithmeticSettingsStore(context) }
    val savedSettings = remember { settingsStore.load() }

    var range by remember { mutableStateOf(savedSettings.range) }
    var formulaCardCount by remember { mutableIntStateOf(savedSettings.cardCount) }
    var selectedOperators by remember { mutableStateOf(savedSettings.operators) }
    var dictationEnabled by remember { mutableStateOf(savedSettings.dictationEnabled) }
    var formulas by remember {
        mutableStateOf(
            newArithmeticFormulaDeck(
                count = formulaCardCount,
                minValue = range.min,
                maxValue = range.max,
                operators = selectedOperators,
            ),
        )
    }
    var roundPhase by remember { mutableStateOf(RoundPhase.Idle) }
    // Cards are presented in `ordered`; `orderedSources` keeps which deck entry each position is,
    // so the log stays keyed by the original question.
    var ordered by remember { mutableStateOf(formulas) }
    var orderedSources by remember { mutableStateOf(formulas.indices.toList()) }
    var isRetryPass by remember { mutableStateOf(false) }
    var retryHintVisible by remember { mutableStateOf(false) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var sliding by remember { mutableStateOf(false) }
    var answerRevealed by remember { mutableStateOf(false) }
    var awaitingAdvance by remember { mutableStateOf(false) }
    var advanceSequence by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }
    var sessionLog by remember { mutableStateOf(PracticeSessionLog.Empty) }
    // True from a correct answer until the card slides away: that is what colours it green. A miss
    // only ever tints the card through the wrong-count colours, because the answer is not revealed.
    var answerCorrect by remember { mutableStateOf(false) }
    var questionStartedAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Null until answering really begins: no round, no clock.
    var roundStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var roundFinishedAtMillis by remember { mutableStateOf<Long?>(null) }
    var hasMicPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    var heardAnswer by remember { mutableStateOf<Int?>(null) }
    var lastJudgedValue by remember { mutableStateOf<Int?>(null) }
    var lastJudgedAtMillis by remember { mutableLongStateOf(0L) }
    // Text of a miss that is still waiting to see whether the number continues.
    var heldText by remember { mutableStateOf<String?>(null) }
    var heldSequence by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }
    val density = LocalDensity.current
    val bottomInset = with(density) {
        maxOf(WindowInsets.navigationBars.getBottom(this).toDp(), BottomEdgeGuard)
    }

    // Settings survive a restart, so a family that practices 10~20 stays on 10~20.
    LaunchedEffect(range, formulaCardCount, selectedOperators, dictationEnabled) {
        settingsStore.save(
            ArithmeticSettings(
                range = range,
                cardCount = formulaCardCount,
                operators = selectedOperators,
                dictationEnabled = dictationEnabled,
            ),
        )
    }

    val compactCardWidth = if (compact) {
        minOf(210.dp, (configuration.screenWidthDp / 2 - 48).coerceAtLeast(120).dp)
    } else {
        0.dp
    }
    val compactCardHeight = if (compact) {
        minOf(270.dp, (configuration.screenHeightDp - 82).coerceAtLeast(160).dp)
    } else {
        0.dp
    }

    val currentSource = orderedSources.getOrNull(targetIndex) ?: 0
    val currentWrongCount = sessionLog.wrongCount(currentSource)
    val cardColor = when {
        roundPhase != RoundPhase.Running -> CardNeutralColor
        answerCorrect -> CardCorrectColor
        currentWrongCount >= MaxWrongAttempts -> CardSecondWrongColor
        currentWrongCount == 1 -> CardFirstWrongColor
        else -> CardNeutralColor
    }
    val cardTextColor = if (currentWrongCount >= MaxWrongAttempts) Color.White else Color.Black
    val currentProblem = ordered.getOrNull(targetIndex)
    val roundTotalMillis = roundStartedAtMillis?.let { started ->
        (roundFinishedAtMillis ?: System.currentTimeMillis()) - started
    }?.coerceAtLeast(0L)

    /** Fresh deck, fresh log, and the first card is only shown once the mic is actually up. */
    val startRound: () -> Unit = {
        formulas = newArithmeticFormulaDeck(
            count = formulaCardCount,
            minValue = range.min,
            maxValue = range.max,
            operators = selectedOperators,
        )
        ordered = formulas
        orderedSources = formulas.indices.toList()
        isRetryPass = false
        retryHintVisible = false
        sessionLog = PracticeSessionLog.Empty
        targetIndex = 0
        sliding = false
        awaitingAdvance = false
        advanceSequence = 0
        answerRevealed = false
        answerCorrect = false
        heardAnswer = null
        lastJudgedValue = null
        roundFinishedAtMillis = null
        roundStartedAtMillis = null
        dictationEnabled = true
        roundPhase = RoundPhase.Preparing
    }

    val restartRound: () -> Unit = {
        startRound()
    }

    val refreshDeck: (ArithmeticRange, Set<ArithmeticOperator>, Int) -> Unit = { newRange, operators, count ->
        formulas = newArithmeticFormulaDeck(
            count = count,
            minValue = newRange.min,
            maxValue = newRange.max,
            operators = operators,
        )
        ordered = formulas
        orderedSources = formulas.indices.toList()
        isRetryPass = false
        retryHintVisible = false
        targetIndex = 0
        sliding = false
        awaitingAdvance = false
        advanceSequence = 0
        answerRevealed = false
        answerCorrect = false
        sessionLog = PracticeSessionLog.Empty
        heardAnswer = null
        lastJudgedValue = null
        roundFinishedAtMillis = null
        roundPhase = RoundPhase.Idle
    }

    /** The finished card sweeps up and away; the next card is timed only once that is over. */
    val slideToNext: () -> Unit = {
        if (!sliding && roundPhase == RoundPhase.Running && targetIndex < ordered.lastIndex) {
            sliding = true
        }
    }

    val finishRound: (Long) -> Unit = { now ->
        answerRevealed = true
        roundFinishedAtMillis = now
        roundPhase = RoundPhase.Finished
        // Everything is answered: release the microphone and show the report.
        dictationEnabled = false
        showRecords = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Everything on the main pass has been answered. Questions that were never right get exactly
     * one more go, and only then does the round end.
     */
    val proceedAfterPass: (Long) -> Unit = { now ->
        // The retry pass asks the questions that were never right, in deck order.
        val missed = orderedSources.filter { sessionLog.correctCount(it) == 0 }
        when (val outcome = nextPass(missed, isRetryPass)) {
            is PassOutcome.Retry -> {
                ordered = outcome.indices.map { formulas[it] }
                orderedSources = outcome.indices
                targetIndex = 0
                isRetryPass = true
                retryHintVisible = true
                sliding = false
                awaitingAdvance = false
                advanceSequence = 0
                answerRevealed = false
                answerCorrect = false
                heardAnswer = null
                lastJudgedValue = null
                questionStartedAtMillis = now
            }
            PassOutcome.Finished -> finishRound(now)
        }
    }

    val onSlideFinished: () -> Unit = {
        if (targetIndex < ordered.lastIndex) {
            targetIndex += 1
            answerRevealed = false
            answerCorrect = false
            heardAnswer = null
            lastJudgedValue = null
            awaitingAdvance = false
            advanceSequence = 0
            // The clock for the new question starts here, not when the previous one was answered.
            questionStartedAtMillis = System.currentTimeMillis()
        }
        sliding = false
    }

    val submitAnswer: (Int) -> Unit = submit@{ value ->
        val problem = currentProblem ?: return@submit
        if (roundPhase != RoundPhase.Running || sliding || awaitingAdvance) return@submit
        val now = System.currentTimeMillis()
        val elapsedMillis = (now - questionStartedAtMillis).coerceAtLeast(0L)
        val correct = value == problem.answer
        val updatedLog = sessionLog.record(
            index = currentSource,
            prompt = problem.expression,
            answer = problem.answer.toString(),
            heard = value.toString(),
            correct = correct,
            elapsedMillis = elapsedMillis,
            answeredAtMillis = now,
        )
        sessionLog = updatedLog
        questionStartedAtMillis = now
        lastJudgedValue = null
        lastJudgedAtMillis = 0L
        if (correct) {
            answerCorrect = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // Hold the solved card for a moment so the answer is actually seen. The last card of a
            // pass takes this same route, so the pass decision — and with it the retry pass — is
            // never skipped.
            awaitingAdvance = true
            advanceSequence += 1
        } else {
            // A miss only colours the card — the correct answer is deliberately not given away —
            // and the deck moves straight on.
            answerCorrect = false
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            awaitingAdvance = true
            advanceSequence += 1
        }
    }

    /** Records an answer, skipping one the recogniser has just repeated. */
    val commitAnswer: (Int) -> Unit = commit@{ value ->
        val now = System.currentTimeMillis()
        val isRepeated = value == lastJudgedValue && now - lastJudgedAtMillis < RepeatedUtteranceGuardMillis
        if (isRepeated) return@commit
        lastJudgedValue = value
        lastJudgedAtMillis = now
        submitAnswer(value)
    }

    val handleSpokenAnswers: (List<String>) -> Unit = { candidates ->
        // While a solved card is being shown or the deck is sliding, extra speech is ignored so it
        // cannot overwrite the answer on screen or land on the next question.
        if (roundPhase == RoundPhase.Running && !sliding && !awaitingAdvance) {
            val raw = candidates.firstOrNull().orEmpty().filterNot { it.isWhitespace() || it == '[' || it == ']' }
                .replace("unk", "")
            val combined = (heldText ?: "") + raw
            val value = SpokenNumberParser.parse(combined)
            val expected = currentProblem?.answer
            if (value != null && value == expected) {
                // This piece completes the answer — possibly the tail of a drawn out number.
                heldText = null
                heardAnswer = value
                commitAnswer(value)
            } else if (combined.isNotBlank()) {
                // Hold the miss for a moment: the rest of the number may still be coming.
                heldText = combined
                heardAnswer = value
                heldSequence += 1
            }
        }
    }

    LaunchedEffect(heldSequence) {
        if (heldSequence > 0) {
            delay(UtteranceMergeWindowMillis)
            val held = heldText
            heldText = null
            SpokenNumberParser.parse(held)?.let { commitAnswer(it) }
        }
    }

    // A new question must never inherit half of the previous answer.
    LaunchedEffect(targetIndex, roundPhase) {
        heldText = null
        heldSequence = 0
    }

    val roundActive = roundPhase == RoundPhase.Preparing || roundPhase == RoundPhase.Running
    val dictation = rememberDictationEngine(
        active = dictationEnabled && roundActive && hasMicPermission && !showSettings && !showRecords,
        onAnswers = handleSpokenAnswers,
    )
    val voiceReady = !dictation.available || !hasMicPermission ||
        dictation.phase == DictationPhase.Listening

    // The recogniser has to be awake before the first question appears, so a child cannot start
    // answering while the model is still loading.
    LaunchedEffect(roundPhase, voiceReady) {
        if (roundPhase == RoundPhase.Preparing && voiceReady) {
            // The first question is on screen now, so the round clock starts here.
            val now = System.currentTimeMillis()
            roundStartedAtMillis = now
            questionStartedAtMillis = now
            roundPhase = RoundPhase.Running
        }
    }

    // A fresh question resumes judging: drop whatever was said while the finished card was on
    // screen. The recorder itself never stops, so this is all it takes to resume.
    LaunchedEffect(targetIndex, roundPhase) {
        if (roundPhase == RoundPhase.Running) dictation.flushPendingAudio()
    }

    // After a correct answer the card lingers, then sweeps away.
    LaunchedEffect(advanceSequence) {
        if (advanceSequence > 0) {
            delay(AdvanceDelayMillis)
            if (awaitingAdvance) {
                if (targetIndex < ordered.lastIndex) slideToNext() else proceedAfterPass(System.currentTimeMillis())
            }
        }
    }

    LaunchedEffect(retryHintVisible) {
        if (retryHintVisible) {
            delay(1800L)
            retryHintVisible = false
        }
    }

    PlayboxBackground(dark = true) {
        PlayboxScaffold(
            title = "",
            onBack = onBack,
            actions = {
                MicToggle(
                    phase = dictation.phase,
                    enabled = dictationEnabled,
                    available = dictation.available,
                    permissionGranted = hasMicPermission,
                    roundActive = roundActive,
                    onClick = {
                        when {
                            !dictation.available -> Unit
                            !hasMicPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else -> dictationEnabled = !dictationEnabled
                        }
                    },
                )
                TextButton(onClick = { showRecords = true }) { Text("记录") }
                TextButton(enabled = !sliding, onClick = { showSettings = true }) { Text("设置") }
                Text(
                    text = when {
                        roundPhase == RoundPhase.Idle -> "${formulas.size} 题"
                        isRetryPass -> "补答 ${targetIndex + 1}/${ordered.size}"
                        else -> "${targetIndex + 1}/${ordered.size}"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
        ) { contentModifier ->
            Column(
                // The window is drawn edge to edge, and on some ROMs the navigation bar inset
                // arrives as zero even though the bar still covers the bottom of the screen, so
                // the status line keeps a guaranteed margin of its own.
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(bottom = bottomInset)
                    .padding(horizontal = PlayboxTokens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                val cardWidth: Dp
                val cardHeight: Dp
                if (compact) {
                    cardWidth = compactCardWidth
                    cardHeight = compactCardHeight
                } else {
                    val availableHeight = (configuration.screenHeightDp.dp - CardLayoutReserve)
                        .coerceAtLeast(150.dp)
                    cardWidth = minOf(configuration.screenWidthDp.dp * .8f, availableHeight / CardAspectRatio)
                    cardHeight = cardWidth * CardAspectRatio
                }
                Box(contentAlignment = Alignment.Center) {
                    StackedCardDeck(
                        items = ordered,
                        targetIndex = targetIndex,
                        sliding = sliding,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        stackGapRatio = StackGapRatio,
                        scaleStep = StackScaleStep,
                        frontColor = cardColor,
                        frontTextColor = cardTextColor,
                        showFace = roundPhase == RoundPhase.Running,
                        onSlideFinished = onSlideFinished,
                    ) { formula, style, modifier ->
                        FormulaCardFace(
                            formula = formula,
                            faceVisible = style.faceVisible,
                            answerText = when {
                                style.lane > 0 -> "?"
                                answerRevealed -> formula.answer.toString()
                                heardAnswer != null -> heardAnswer.toString()
                                else -> "?"
                            },
                            answerColor = when {
                                style.lane > 0 -> Color.Black.copy(alpha = .3f)
                                answerRevealed -> cardTextColor
                                heardAnswer != null -> GuessAccent
                                else -> cardTextColor.copy(alpha = .3f)
                            },
                            textColor = style.textColor,
                            cardColor = style.color,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            compact = compact,
                            modifier = modifier,
                        )
                    }
                    when (roundPhase) {
                        RoundPhase.Idle -> RoundActionButton(text = "开始", onClick = {
                            if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            startRound()
                        })
                        RoundPhase.Preparing -> RoundHint(text = "正在准备语音识别…")
                        RoundPhase.Running -> if (retryHintVisible) {
                            RoundHint(text = "补答：还有 ${ordered.size} 题再算一次")
                        }
                        RoundPhase.Finished -> FinishSummary(
                            totalMillis = roundTotalMillis,
                            onRestart = restartRound,
                        )
                        else -> Unit
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showSettings) {
        ArithmeticSettingsSheet(
            range = range,
            formulaCardCount = formulaCardCount,
            selectedOperators = selectedOperators,
            dictationEnabled = dictationEnabled,
            dictationAvailable = dictation.available,
            hasMicPermission = hasMicPermission,
            onDismiss = { showSettings = false },
            onRangeChange = { newRange ->
                range = newRange
                refreshDeck(newRange, selectedOperators, formulaCardCount)
            },
            onOperatorsChange = { operators ->
                selectedOperators = operators
                refreshDeck(range, operators, formulaCardCount)
            },
            onCardCountChange = { count ->
                formulaCardCount = count
                refreshDeck(range, selectedOperators, count)
            },
            onDictationChange = { enabled ->
                if (enabled && !hasMicPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                dictationEnabled = enabled
            },
        )
    }

    if (showRecords) {
        ArithmeticRecordsSheet(
            log = sessionLog,
            formulas = formulas,
            roundStartedAtMillis = roundStartedAtMillis,
            roundFinishedAtMillis = roundFinishedAtMillis,
            onDismiss = { showRecords = false },
            onReset = {
                sessionLog = PracticeSessionLog.Empty
                roundStartedAtMillis = null
                roundFinishedAtMillis = null
            },
        )
    }
}

/**
 * The deck itself: the current question on top, up to two more cards peeking out below it. When a
 * question is finished the top card sweeps straight up and out, and the cards underneath move up
 * one place.
 */
@Composable
private fun FormulaCardFace(
    formula: ArithmeticProblem,
    faceVisible: Boolean,
    answerText: String,
    answerColor: Color,
    textColor: Color,
    cardColor: Color,
    cardWidth: Dp,
    cardHeight: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val formulaFontSize =
        (cardWidth.value * (if (compact) FormulaFontRatioCompact else FormulaFontRatio) / fontScale).sp
    val formulaWidth = cardHeight * .94f
    val symbolSlotWidth = cardWidth * (if (compact) SymbolSlotRatioCompact else SymbolSlotRatio)
    val numberSlotWidth = (formulaWidth - symbolSlotWidth * 2) / 3
    val rowThickness =
        cardWidth * (if (compact) FormulaRowThicknessRatioCompact else FormulaRowThicknessRatio)
    Surface(
        modifier = modifier.size(cardWidth, cardHeight),
        shape = RoundedCornerShape(cardWidth * CardCornerRatio),
        color = cardColor,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (faceVisible) {
                Row(
                    modifier = Modifier
                        .requiredWidth(formulaWidth)
                        .height(rowThickness)
                        .graphicsLayer { rotationZ = 90f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormulaSlot(formula.left.toString(), numberSlotWidth, formulaFontSize, textColor)
                    FormulaSlot(formula.operator.symbol, symbolSlotWidth, formulaFontSize, textColor)
                    FormulaSlot(formula.right.toString(), numberSlotWidth, formulaFontSize, textColor)
                    FormulaSlot("=", symbolSlotWidth, formulaFontSize, textColor)
                    FormulaSlot(answerText, numberSlotWidth, formulaFontSize, answerColor)
                }
            }
        }
    }
}

@Composable
private fun FormulaSlot(
    text: String,
    width: Dp,
    fontSize: TextUnit,
    color: Color,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
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
private fun ArithmeticSettingsSheet(
    range: ArithmeticRange,
    formulaCardCount: Int,
    selectedOperators: Set<ArithmeticOperator>,
    dictationEnabled: Boolean,
    dictationAvailable: Boolean,
    hasMicPermission: Boolean,
    onDismiss: () -> Unit,
    onRangeChange: (ArithmeticRange) -> Unit,
    onOperatorsChange: (Set<ArithmeticOperator>) -> Unit,
    onCardCountChange: (Int) -> Unit,
    onDictationChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SheetTitle(title = "算术转盘设置", onDismiss = onDismiss)
            Spacer(Modifier.height(18.dp))
            Text("结果范围", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "答案落在这个范围内，参与计算的数不会超过最大值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(10.dp))
            RangePresets.chunked(3).forEach { presets ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        SettingChoice(
                            text = preset.label,
                            selected = preset == range,
                            modifier = Modifier.weight(1f),
                            onClick = { onRangeChange(preset) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RangeBoundStepper(
                    label = "最小值",
                    value = range.min,
                    modifier = Modifier.weight(1f),
                    onDecrease = { onRangeChange(arithmeticRange(range.min - ArithmeticRangeStep, range.max)) },
                    onIncrease = { onRangeChange(arithmeticRange(range.min + ArithmeticRangeStep, range.max)) },
                )
                RangeBoundStepper(
                    label = "最大值",
                    value = range.max,
                    modifier = Modifier.weight(1f),
                    onDecrease = { onRangeChange(arithmeticRange(range.min, range.max - ArithmeticRangeStep)) },
                    onIncrease = { onRangeChange(arithmeticRange(range.min, range.max + ArithmeticRangeStep)) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "当前：${range.label}（减法不会出现负数，除法一定能整除）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
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
            Text("卡片数量", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(Modifier.height(22.dp))
            HorizontalDivider()
            Spacer(Modifier.height(18.dp))
            Text("语音听写", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("说出答案自动判定", fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            !dictationAvailable -> "离线语音模型不可用，可手动作答"
                            !hasMicPermission -> "开启后会请求麦克风权限"
                            else -> "完全离线识别，答对自动进入下一题"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Switch(
                    checked = dictationEnabled,
                    enabled = dictationAvailable,
                    onCheckedChange = onDictationChange,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "以上设置会保存在本机，下次打开继续沿用。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Sheet heading with a close button in the corner. */
@Composable
private fun SheetTitle(title: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RangeBoundStepper(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(text = "−", onClick = onDecrease, modifier = Modifier.weight(1f))
            Text(
                text = value.toString(),
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            StepButton(text = "+", onClick = onIncrease, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArithmeticRecordsSheet(
    log: PracticeSessionLog,
    formulas: List<ArithmeticProblem>,
    roundStartedAtMillis: Long?,
    roundFinishedAtMillis: Long?,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val averageMillis = log.averageFirstCorrectMillis
    // Keeps the round clock live while the sheet is open and the run is still going.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(roundStartedAtMillis, roundFinishedAtMillis) {
        while (roundStartedAtMillis != null && roundFinishedAtMillis == null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val roundTotalMillis = roundStartedAtMillis?.let { started ->
        ((roundFinishedAtMillis ?: nowMillis) - started).coerceAtLeast(0L)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("答题记录", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(enabled = log.records.isNotEmpty(), onClick = onReset) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "已作答 ${log.answeredCount}/${formulas.size} 题 · " +
                    "正确 ${log.totalCorrect} 次 · 错误 ${log.totalWrong} 次 · " +
                    "正确率 ${(log.accuracy * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = roundTotalMillis?.let { total ->
                    "本轮总用时 ${formatElapsed(total)}" +
                        if (roundFinishedAtMillis != null) "（已完成）" else "（进行中）"
                } ?: "本轮尚未开始作答",
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "答题累计 ${formatElapsed(log.totalElapsedMillis)}" +
                    (averageMillis?.let { " · 平均每题 ${formatElapsed(it)}" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(16.dp))
            formulas.forEachIndexed { index, problem ->
                QuestionRecordRow(
                    index = index,
                    problem = problem,
                    record = log.recordFor(index),
                    averageMillis = averageMillis,
                    expanded = expandedIndex == index,
                    onToggle = { expandedIndex = if (expandedIndex == index) null else index },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QuestionRecordRow(
    index: Int,
    problem: ArithmeticProblem,
    record: PracticeQuestionRecord?,
    averageMillis: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val answered = record?.answered == true
    val wrongCount = record?.wrongCount ?: 0
    val questionMillis = record?.firstCorrectMillis
    val slowerThanAverage = questionMillis != null && averageMillis != null && questionMillis > averageMillis
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    // A card that was missed keeps a warm tint, so the mistakes stand out while scrolling.
    val containerColor = if (wrongCount > 0) {
        WrongAccent.copy(alpha = .18f).compositeOver(baseColor)
    } else {
        baseColor
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = answered, onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${problem.expression} = ${problem.answer}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                if (record == null) {
                    Text("未作答", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(
                        text = "✅${record.correctCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "❌${record.wrongCount}",
                        color = if (wrongCount > 0) WrongAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (wrongCount > 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = questionMillis?.let { formatElapsed(it) } ?: "—",
                        color = if (slowerThanAverage) AboveAverageAccent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (expanded && record != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "答对用时：" + record.correctTimesMillis.joinToString("、") { formatElapsed(it) }
                        .ifEmpty { "尚无答对记录" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (slowerThanAverage) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "⚠ 比平均用时慢",
                        style = MaterialTheme.typography.labelMedium,
                        color = AboveAverageAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                record.attempts.forEachIndexed { attemptIndex, attempt ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "第 ${attemptIndex + 1} 次 · " +
                            (if (attempt.correct) "答对" else "答错（说了 ${attempt.heard}）") +
                            " · 用时 ${formatElapsed(attempt.elapsedMillis)}" +
                            " · ${formatClockTime(attempt.answeredAtMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (attempt.correct) CorrectAccent else WrongAccent,
                    )
                }
            }
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
