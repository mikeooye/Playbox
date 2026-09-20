package com.playbox.games.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.util.formatElapsed

/**
 * The practice-card deck shared by the arithmetic and pinyin tools: one card in front, two more
 * peeking out behind it, and a finished card that lifts off and dissolves to reveal the next one.
 *
 * Only the tuning values and the face of a card differ between the two tools, so the geometry,
 * the timing and the palette live here instead of being copied into each screen.
 */

/** How long a finished card (solved, or missed twice) stays readable before it slides away. */
const val AdvanceDelayMillis = 500L

/** How long a finished card takes to lift off and dissolve. */
const val CardSlideDurationMillis = 250

/** How far the card rises before it is gone, as a share of its own height. */
const val CardSlideRiseRatio = .2f

/** Share of the slide after which the leaving card starts to fade away. */
const val CardFadeStartRatio = .55f

/** Cards drawn behind the current one, including it. */
const val StackDepth = 3

/** The card only dissolves near the end, once it has already lifted most of the way out. */
fun cardFadeAlpha(progress: Float): Float =
    (1f - ((progress - CardFadeStartRatio) / (1f - CardFadeStartRatio))).coerceIn(0f, 1f)

val CardSlideEasing = CubicBezierEasing(.4f, 0f, .2f, 1f)

val CardNeutralColor = Color.White
val CardFirstWrongColor = Color(0xFFFFB74D)
val CardSecondWrongColor = Color(0xFFE53935)
val CardCorrectColor = Color(0xFF66D19E)
val CorrectAccent = Color(0xFF66D19E)
val WrongAccent = Color(0xFFFFB74D)
val AboveAverageAccent = Color(0xFFFF8A65)

private val CardBackColor = Color(0xFFE8E6EF)
private val CardBackTextColor = Color.Black.copy(alpha = .45f)

/** What one lane of the deck needs to draw its card. */
data class CardLaneStyle(
    /** Zero is the card in play; higher lanes are the ones waiting behind it. */
    val lane: Int,
    val faceVisible: Boolean,
    val color: Color,
    val textColor: Color,
)

/**
 * Draws [items] as a deck of cards, the one at [targetIndex] on top.
 *
 * When [sliding] turns true the top card rises, fades and finally calls [onSlideFinished], which is
 * where the owner advances [targetIndex]. Cards behind the front one stay put and simply take its
 * place, because they are drawn at a slightly smaller scale, a little lower down.
 *
 * @param stackGapRatio how far apart the waiting cards sit, as a share of [cardHeight].
 * @param scaleStep how much smaller each waiting card is, so the tools can tune their own look.
 * @param card draws one card face; [Modifier] carries the lane's offset, scale and fade and must be
 *   applied by the face (the card's own size modifier belongs after it).
 */
@Composable
fun <T> StackedCardDeck(
    items: List<T>,
    targetIndex: Int,
    sliding: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    stackGapRatio: Float,
    scaleStep: Float,
    frontColor: Color,
    frontTextColor: Color,
    showFace: Boolean,
    onSlideFinished: () -> Unit,
    card: @Composable (item: T, style: CardLaneStyle, modifier: Modifier) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(sliding) {
        if (sliding) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(CardSlideDurationMillis, easing = CardSlideEasing),
            )
            onSlideFinished()
        } else {
            progress.snapTo(0f)
        }
    }
    // The progress is only meaningful while a card is actually sweeping away; reading it as zero
    // otherwise keeps the newly promoted card from flashing off-screen for a frame.
    val slide = if (sliding) progress.value else 0f
    val stackGap = cardHeight * stackGapRatio

    Box(
        modifier = Modifier.size(cardWidth, cardHeight),
        contentAlignment = Alignment.TopStart,
    ) {
        val depth = minOf(StackDepth, items.size - targetIndex)
        for (lane in (depth - 1) downTo 0) {
            val item = items[targetIndex + lane]
            // Only the finished card moves: the ones underneath stay put and hidden behind it,
            // and simply take its place once it has dissolved.
            val offsetY = if (lane == 0) {
                -(slide * cardHeight.value * CardSlideRiseRatio).dp
            } else {
                stackGap * lane
            }
            val scale = if (lane == 0) 1f else (1f - scaleStep * lane).coerceIn(.8f, 1f)
            val cardAlpha = if (lane == 0) cardFadeAlpha(slide) else 1f
            card(
                item,
                CardLaneStyle(
                    lane = lane,
                    faceVisible = showFace && lane <= 1,
                    color = if (lane == 0) frontColor else CardBackColor,
                    textColor = if (lane == 0) frontTextColor else CardBackTextColor,
                ),
                Modifier
                    .offset(y = offsetY)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = cardAlpha
                        translationY = (cardHeight.toPx() * (1f - scale)) / 2f
                    },
            )
        }
    }
}

/** Big, obvious call to action drawn on top of the deck (start / play again). */
@Composable
fun RoundActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.height(56.dp),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** A short banner over the deck, used while the recogniser wakes up or a retry pass starts. */
@Composable
fun RoundHint(text: String) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Black.copy(alpha = .55f)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Wraps up a finished round right on the card: the round total and a way to go again. */
@Composable
fun FinishSummary(totalMillis: Long?, onRestart: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Black.copy(alpha = .72f)) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉 全部完成", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (totalMillis != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本轮总用时 ${formatElapsed(totalMillis)}",
                    color = CorrectAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            RoundActionButton(text = "再来一轮", onClick = onRestart)
        }
    }
}
