package com.playbox.games.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.ui.dictation.DictationPhase

private val MicListeningColor = Color(0xFF66D19E)
private val MicPausedColor = Color(0xFFFFB74D)

/**
 * The dictation switch that lives in the top bar. The label and the icon both change with the
 * state, and the microphone pulses while it is live, so "on" and "off" are never ambiguous.
 */
@Composable
fun MicToggle(
    phase: DictationPhase,
    enabled: Boolean,
    available: Boolean,
    permissionGranted: Boolean,
    roundActive: Boolean,
    onClick: () -> Unit,
) {
    // The recorder runs for the whole round, so this state must not flicker between questions.
    val listening = enabled && permissionGranted && available &&
        roundActive && phase != DictationPhase.Loading && phase != DictationPhase.Error
    val preparing = enabled && roundActive && phase == DictationPhase.Loading
    val muted = !available || !enabled
    val containerColor = when {
        listening -> MicListeningColor.copy(alpha = .32f)
        !enabled && available -> MicPausedColor.copy(alpha = .18f)
        else -> Color.White.copy(alpha = .12f)
    }
    val contentColor = when {
        !available -> Color.White.copy(alpha = .38f)
        !enabled -> MicPausedColor
        listening -> MicListeningColor
        phase == DictationPhase.Error -> MicPausedColor
        else -> Color.White.copy(alpha = .85f)
    }
    val label = when {
        !available -> "听写关"
        !enabled -> "已暂停"
        phase == DictationPhase.Error -> "重试中"
        preparing -> "准备中"
        listening -> "听写中"
        else -> "听写"
    }
    Surface(
        modifier = Modifier
            .height(38.dp)
            .clickable(enabled = available, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicGlyph(slashed = muted, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A drawn microphone: filled while listening, slashed while dictation is paused or missing. */
@Composable
private fun MicGlyph(slashed: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val stroke = width * .11f
        drawRoundRect(
            color = tint,
            topLeft = Offset(width * .35f, height * .07f),
            size = Size(width * .3f, height * .5f),
            cornerRadius = CornerRadius(width * .15f),
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * .18f, height * .24f),
            size = Size(width * .64f, height * .52f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(width * .5f, height * .74f),
            end = Offset(width * .5f, height * .92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(width * .28f, height * .92f),
            end = Offset(width * .72f, height * .92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (slashed) {
            drawLine(
                color = tint,
                start = Offset(width * .1f, height * .12f),
                end = Offset(width * .9f, height * .88f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
