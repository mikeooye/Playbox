package com.playbox.games.ui.pinyin

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.playbox.games.util.PassOutcome
import com.playbox.games.util.PinyinCardCounts
import com.playbox.games.util.PinyinSyllable
import com.playbox.games.util.PracticeQuestionRecord
import com.playbox.games.util.PracticeSessionLog
import com.playbox.games.util.formatClockTime
import com.playbox.games.util.formatElapsed
import com.playbox.games.util.newPinyinDeck
import com.playbox.games.util.nextPass
import com.playbox.games.util.UnknownWord
import com.playbox.games.util.isCorrectReading
import com.playbox.games.util.pinyinCardGrammarJson
import kotlinx.coroutines.delay

/** A card is missed twice before it reddens and moves on, exactly like the arithmetic deck. */
private const val MaxWrongAttempts = 2

private const val CardAspectRatio = 1.55f
private const val RepeatedUtteranceGuardMillis = 600L

/** A drawn out syllable arrives as two pieces; a miss is held this long so they can be merged. */
private const val UtteranceMergeWindowMillis = 900L

// How the deck is stacked: the cards behind the top one sit only a hair lower and a hair
// smaller, so a thin edge is all that shows.
private const val StackGapRatio = .012f
private const val StackScaleStep = .02f

private val CardLayoutReserve = 200.dp
private val BottomEdgeGuard = 46.dp

private enum class RoundPhase { Idle, Preparing, Running, Finished }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinyinScreen(onBack: (() -> Unit)?, compact: Boolean = false) {
    val context = LocalContext.current
    val store = remember(context) { PinyinSettingsStore(context) }
    val saved = remember { store.load() }

    var cardCount by remember { mutableIntStateOf(saved) }
    var deck by remember { mutableStateOf(newPinyinDeck(cardCount)) }
    var roundPhase by remember { mutableStateOf(RoundPhase.Idle) }
    // Cards are presented in `ordered`; `orderedSources` keeps which deck entry each position is.
    var ordered by remember { mutableStateOf(deck) }
    var orderedSources by remember { mutableStateOf(deck.indices.toList()) }
    var isRetryPass by remember { mutableStateOf(false) }
    var retryHintVisible by remember { mutableStateOf(false) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var sliding by remember { mutableStateOf(false) }
    var awaitingAdvance by remember { mutableStateOf(false) }
    var advanceSequence by remember { mutableIntStateOf(0) }
    var feedbackCorrect by remember { mutableStateOf(false) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }
    var sessionLog by remember { mutableStateOf(PracticeSessionLog.Empty) }
    var questionStartedAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var roundStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var roundFinishedAtMillis by remember { mutableStateOf<Long?>(null) }
    var heardText by remember { mutableStateOf<String?>(null) }
    var dictationEnabled by remember { mutableStateOf(true) }
    var hasMicPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    var lastJudgedAtMillis by remember { mutableLongStateOf(0L) }
    var lastJudgedHeard by remember { mutableStateOf<String?>(null) }
    var heldText by remember { mutableStateOf<String?>(null) }
    var heldSequence by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }
    val bottomInset = with(density) {
        maxOf(WindowInsets.navigationBars.getBottom(this).toDp(), BottomEdgeGuard)
    }
    LaunchedEffect(cardCount) { store.save(cardCount) }

    val currentSource = orderedSources.getOrNull(targetIndex) ?: 0
    val current = ordered.getOrNull(targetIndex)
    val cardColor = when {
        roundPhase != RoundPhase.Running -> CardNeutralColor
        feedbackCorrect -> CardCorrectColor
        wrongCount >= MaxWrongAttempts -> CardSecondWrongColor
        wrongCount == 1 -> CardFirstWrongColor
        else -> CardNeutralColor
    }
    val cardTextColor = if (wrongCount >= MaxWrongAttempts) Color.White else Color.Black
    val roundTotalMillis = roundStartedAtMillis?.let { started ->
        (roundFinishedAtMillis ?: System.currentTimeMillis()) - started
    }?.coerceAtLeast(0L)

    val startRound: () -> Unit = {
        deck = newPinyinDeck(cardCount)
        ordered = deck
        orderedSources = deck.indices.toList()
        isRetryPass = false
        retryHintVisible = false
        sessionLog = PracticeSessionLog.Empty
        targetIndex = 0
        sliding = false
        awaitingAdvance = false
        advanceSequence = 0
        feedbackCorrect = false
        wrongCount = 0
        heardText = null
        roundFinishedAtMillis = null
        roundStartedAtMillis = null
        dictationEnabled = true
        roundPhase = RoundPhase.Preparing
    }

    val refreshDeck: (Int) -> Unit = { count ->
        deck = newPinyinDeck(count)
        ordered = deck
        orderedSources = deck.indices.toList()
        isRetryPass = false
        retryHintVisible = false
        targetIndex = 0
        sliding = false
        awaitingAdvance = false
        advanceSequence = 0
        feedbackCorrect = false
        wrongCount = 0
        heardText = null
        sessionLog = PracticeSessionLog.Empty
        roundFinishedAtMillis = null
        roundStartedAtMillis = null
        roundPhase = RoundPhase.Idle
    }

    val slideToNext: () -> Unit = {
        if (!sliding && roundPhase == RoundPhase.Running && targetIndex < ordered.lastIndex) sliding = true
    }

    val onSlideFinished: () -> Unit = {
        if (targetIndex < ordered.lastIndex) {
            targetIndex += 1
            feedbackCorrect = false
            wrongCount = 0
            heardText = null
            lastJudgedHeard = null
            awaitingAdvance = false
            advanceSequence = 0
            questionStartedAtMillis = System.currentTimeMillis()
        }
        sliding = false
    }

    val finishRound: (Long) -> Unit = { now ->
        roundFinishedAtMillis = now
        roundPhase = RoundPhase.Finished
        dictationEnabled = false
        showRecords = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * The main pass is over. Questions that were never read correctly get exactly one more go
     * before the round ends.
     */
    val proceedAfterPass: (Long) -> Unit = { now ->
        // The retry pass asks the questions that were never right, in deck order.
        val missed = orderedSources.filter { sessionLog.correctCount(it) == 0 }
        when (val outcome = nextPass(missed, isRetryPass)) {
            is PassOutcome.Retry -> {
                ordered = outcome.indices.map { deck[it] }
                orderedSources = outcome.indices
                targetIndex = 0
                isRetryPass = true
                retryHintVisible = true
                sliding = false
                awaitingAdvance = false
                advanceSequence = 0
                feedbackCorrect = false
                wrongCount = 0
                heardText = null
                lastJudgedHeard = null
                questionStartedAtMillis = now
            }
            PassOutcome.Finished -> finishRound(now)
        }
    }

    val submitAnswer: (String) -> Unit = submit@{ rawText ->
        val syllable = current ?: return@submit
        if (roundPhase != RoundPhase.Running || sliding || awaitingAdvance) return@submit
        val now = System.currentTimeMillis()
        val elapsed = (now - questionStartedAtMillis).coerceAtLeast(0L)
        val correct = isCorrectReading(rawText, syllable)
        val updated = sessionLog.record(
            index = currentSource,
            prompt = syllable.label,
            answer = syllable.display,
            heard = rawText,
            correct = correct,
            elapsedMillis = elapsed,
            answeredAtMillis = now,
        )
        sessionLog = updated
        questionStartedAtMillis = now
        lastJudgedHeard = null
        if (correct) {
            feedbackCorrect = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            awaitingAdvance = true
            advanceSequence += 1
        } else {
            feedbackCorrect = false
            wrongCount = updated.wrongCount(currentSource)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // A miss moves straight on; the question gets its single second chance after the pass.
            awaitingAdvance = true
            advanceSequence += 1
        }
    }

    /** Records a reading, skipping one the recogniser has just repeated. */
    val commitReading: (String) -> Unit = commit@{ rawText ->
        val now = System.currentTimeMillis()
        val repeated = rawText == lastJudgedHeard && now - lastJudgedAtMillis < RepeatedUtteranceGuardMillis
        if (repeated) return@commit
        lastJudgedHeard = rawText
        lastJudgedAtMillis = now
        submitAnswer(rawText)
    }

    val dictation = rememberDictationEngine(
        active = dictationEnabled && hasMicPermission && !showSettings && !showRecords &&
            (roundPhase == RoundPhase.Preparing || roundPhase == RoundPhase.Running),
        grammarJson = current?.let { pinyinCardGrammarJson(it) } ?: "[]",
        onAnswers = { candidates ->
            val raw = candidates.firstOrNull().orEmpty()
            if (roundPhase == RoundPhase.Running && !sliding && !awaitingAdvance && raw.isNotBlank()) {
                val syllable = current
                val combined = (heldText ?: "") + raw
                if (syllable != null && isCorrectReading(combined, syllable)) {
                    // This piece completes the reading, so nothing is counted against the child.
                    heldText = null
                    heardText = raw
                    commitReading(raw)
                } else {
                    // Hold the miss: the rest of a drawn out syllable may still be coming.
                    heldText = combined
                    heardText = combined
                    heldSequence += 1
                }
            }
        },
    )

    val voiceReady = !dictation.available || !hasMicPermission || dictation.phase == DictationPhase.Listening
    LaunchedEffect(roundPhase, voiceReady) {
        if (roundPhase == RoundPhase.Preparing && voiceReady) {
            val now = System.currentTimeMillis()
            roundStartedAtMillis = now
            questionStartedAtMillis = now
            roundPhase = RoundPhase.Running
        }
    }

    LaunchedEffect(heldSequence) {
        if (heldSequence > 0) {
            delay(UtteranceMergeWindowMillis)
            val held = heldText
            heldText = null
            if (held != null) commitReading(held)
        }
    }

    // A new card must never inherit half of the previous reading.
    LaunchedEffect(targetIndex, roundPhase) {
        heldText = null
        heldSequence = 0
    }

    // Each card listens only for its own syllable; the swap happens on the capture thread, so the
    // microphone stays open across questions.
    LaunchedEffect(targetIndex, roundPhase, current) {
        if (roundPhase == RoundPhase.Preparing || roundPhase == RoundPhase.Running) {
            current?.let { dictation.setGrammar(pinyinCardGrammarJson(it)) }
        }
    }

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
                    roundActive = roundPhase == RoundPhase.Preparing || roundPhase == RoundPhase.Running,
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
                        roundPhase == RoundPhase.Idle -> "${deck.size} 题"
                        isRetryPass -> "补答 ${targetIndex + 1}/${ordered.size}"
                        else -> "${targetIndex + 1}/${ordered.size}"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
        ) { contentModifier ->
            Column(
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
                    cardWidth = 200.dp
                    cardHeight = 250.dp
                } else {
                    val availableHeight = (configuration.screenHeightDp.dp - CardLayoutReserve).coerceAtLeast(150.dp)
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
                    ) { syllable, style, modifier ->
                        PinyinCardFace(
                            syllable = syllable,
                            faceVisible = style.faceVisible,
                            cardColor = style.color,
                            textColor = style.textColor,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
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
                            RoundHint(text = "补答：还有 ${ordered.size} 题再读一次")
                        }
                        RoundPhase.Finished -> FinishSummary(
                            totalMillis = roundTotalMillis,
                            onRestart = startRound,
                        )
                        else -> Unit
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showSettings) {
        PinyinSettingsSheet(
            cardCount = cardCount,
            onDismiss = { showSettings = false },
            onCardCountChange = { count ->
                cardCount = count
                refreshDeck(count)
            },
        )
    }

    if (showRecords) {
        PinyinRecordsSheet(
            log = sessionLog,
            deck = deck,
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

@Composable
private fun PinyinCardFace(
    syllable: PinyinSyllable,
    faceVisible: Boolean,
    cardColor: Color,
    textColor: Color,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    // Longer syllables ("zhōng") need smaller type so they still fit the card.
    val face = syllable.teachingDisplay
    val pinyinSize = (cardWidth.value * .82f / face.length.coerceAtLeast(3) / fontScale).sp
    val exampleSize = (cardWidth.value * .2f / fontScale).sp
    Surface(
        modifier = modifier.size(cardWidth, cardHeight),
        shape = RoundedCornerShape(cardWidth * .12f),
        color = cardColor,
        shadowElevation = 10.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (faceVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = face,
                        color = textColor,
                        fontSize = pinyinSize,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(cardHeight * .04f))
                    Text(
                        text = syllable.character,
                        color = textColor.copy(alpha = .55f),
                        fontSize = exampleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinyinSettingsSheet(
    cardCount: Int,
    onDismiss: () -> Unit,
    onCardCountChange: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("拼音设置", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "卡片显示音节，孩子读出来即可判定；只判声母韵母，声调不参与判定。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(18.dp))
            Text("卡片数量", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PinyinCardCounts.forEach { count ->
                    SettingChoice(
                        text = count.toString(),
                        selected = count == cardCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardCountChange(count) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "设置会保存在本机，下次打开继续沿用。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinyinRecordsSheet(
    log: PracticeSessionLog,
    deck: List<PinyinSyllable>,
    roundStartedAtMillis: Long?,
    roundFinishedAtMillis: Long?,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val averageMillis = log.averageFirstCorrectMillis
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
                Text("练习记录", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(enabled = log.records.isNotEmpty(), onClick = onReset) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "已读 ${log.answeredCount}/${deck.size} 题 · 正确 ${log.totalCorrect} 次 · " +
                    "错误 ${log.totalWrong} 次 · 正确率 ${(log.accuracy * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = roundTotalMillis?.let { total ->
                    "本轮总用时 ${formatElapsed(total)}" +
                        if (roundFinishedAtMillis != null) "（已完成）" else "（进行中）"
                } ?: "本轮尚未开始",
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
            deck.forEachIndexed { index, syllable ->
                PinyinRecordRow(
                    index = index,
                    syllable = syllable,
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
private fun PinyinRecordRow(
    index: Int,
    syllable: PinyinSyllable,
    record: PracticeQuestionRecord?,
    averageMillis: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val wrong = record?.wrongCount ?: 0
    val questionMillis = record?.firstCorrectMillis
    val slowerThanAverage = questionMillis != null && averageMillis != null && questionMillis > averageMillis
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = record?.answered == true, onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = if (wrong > 0) WrongAccent.copy(alpha = .18f).compositeOver(base) else base,
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
                    text = "${syllable.teachingDisplay}  ${syllable.character}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                if (record == null) {
                    Text("未读", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("✅${record.correctCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "❌${record.wrongCount}",
                        color = if (wrong > 0) WrongAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (wrong > 0) FontWeight.Bold else FontWeight.Normal,
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
                if (slowerThanAverage) {
                    Text(
                        text = "⚠ 比平均用时慢",
                        style = MaterialTheme.typography.labelMedium,
                        color = AboveAverageAccent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                record.attempts.forEachIndexed { attemptIndex, attempt ->
                    Text(
                        text = "第 ${attemptIndex + 1} 次 · " +
                            (if (attempt.correct) "读对" else {
                                val heardLabel = attempt.heard.replace(UnknownWord, "").trim()
                                if (heardLabel.isEmpty()) "没听清" else "读错（听到 $heardLabel）"
                            }) +
                            " · 用时 ${formatElapsed(attempt.elapsedMillis)}" +
                            " · ${formatClockTime(attempt.answeredAtMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (attempt.correct) CorrectAccent else WrongAccent,
                    )
                    Spacer(Modifier.height(4.dp))
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
