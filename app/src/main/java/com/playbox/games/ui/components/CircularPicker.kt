package com.playbox.games.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val OverviewItemExtentRatio = .26f
private const val OverviewOrbitRadiusRatio = .3f
private const val CloseOrbitRadiusRatio = 1.1f
private const val ReferenceOptionCount = 6f
private const val AlmostFullyVisibleRatio = .85f
private const val PointerVisibilityCount = 3

@Composable
fun <T> CircularPicker(
    items: List<T>,
    targetIndex: Int,
    spinSequence: Int,
    spinning: Boolean,
    durationMillis: Int,
    cameraHeight: Float,
    viewportWidth: Dp,
    viewportHeight: Dp,
    itemWidth: Dp,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
    minimumTurns: Int = 1,
    spinStepDelta: Int? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    require(items.isNotEmpty())
    val safeTargetIndex = targetIndex.coerceIn(items.indices)
    val anglePerItem = 360f / items.size
    val rotation = remember(items.size) { Animatable(safeTargetIndex * anglePerItem) }
    val latestTargetIndex by rememberUpdatedState(safeTargetIndex)

    LaunchedEffect(spinSequence, spinning, safeTargetIndex, spinStepDelta) {
        if (spinning && spinSequence > 0) {
            val normalizedStep = (rotation.value / anglePerItem).roundToInt().mod(items.size)
            rotation.snapTo(normalizedStep * anglePerItem)
            val targetSteps = spinStepDelta?.let { normalizedStep + it } ?: run {
                val firstStepAfterTurns = normalizedStep + items.size * minimumTurns.coerceAtLeast(0)
                firstStepAfterTurns +
                    (latestTargetIndex - firstStepAfterTurns.mod(items.size)).mod(items.size)
            }
            rotation.animateTo(
                targetValue = targetSteps * anglePerItem,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = CubicBezierEasing(.12f, .72f, .16f, 1f),
                ),
            )
        } else {
            rotation.snapTo(latestTargetIndex * anglePerItem)
        }
    }

    val density = LocalDensity.current
    val viewportWidthPx = with(density) { viewportWidth.toPx() }
    val viewportHeightPx = with(density) { viewportHeight.toPx() }
    val baseItemWidthPx = with(density) { itemWidth.toPx() }
    val baseItemHeightPx = with(density) { itemHeight.toPx() }
    val height = cameraHeight.coerceIn(0f, 1f)
    val minViewportExtent = min(viewportWidthPx, viewportHeightPx)
    val closeRadiusScale = optionSpacingRadiusScale(items.size)
    val overviewRadiusScale = sqrt(items.size / ReferenceOptionCount).coerceIn(.7f, 1.3f)
    val closeRadius = viewportHeightPx * CloseOrbitRadiusRatio * closeRadiusScale
    val overviewRadius = minViewportExtent * OverviewOrbitRadiusRatio * overviewRadiusScale
    val overviewItemExtent = min(
        minViewportExtent * OverviewItemExtentRatio,
        2f * overviewRadius * sin(Math.PI / items.size).toFloat() * .78f,
    )
    val overviewScale = min(
        overviewItemExtent / baseItemWidthPx,
        overviewItemExtent / baseItemHeightPx,
    ).coerceAtMost(1f)
    val itemScale = lerp(1f, overviewScale, height)
    val renderedItemWidth = itemWidth * itemScale
    val renderedItemHeight = itemHeight * itemScale
    val renderedItemWidthPx = baseItemWidthPx * itemScale
    val renderedItemHeightPx = baseItemHeightPx * itemScale
    val orbitRadius = lerp(closeRadius, overviewRadius, height)
    val circleCenterX = viewportWidthPx / 2f
    val circleCenterY = lerp(viewportHeightPx / 2f + closeRadius, viewportHeightPx / 2f, height)

    val itemPlacements = items.indices.map { index ->
        val angle = normalizeAngle(index * anglePerItem - rotation.value)
        val radians = Math.toRadians(angle.toDouble())
        PickerItemPlacement(
            index = index,
            angle = angle,
            centerX = circleCenterX + sin(radians).toFloat() * orbitRadius,
            centerY = circleCenterY - cos(radians).toFloat() * orbitRadius,
        )
    }
    val almostFullyVisibleCount = itemPlacements.count { placement ->
        val visibleHalfWidth = renderedItemWidthPx * AlmostFullyVisibleRatio / 2f
        val visibleHalfHeight = renderedItemHeightPx * AlmostFullyVisibleRatio / 2f
        placement.centerX - visibleHalfWidth >= 0f &&
            placement.centerX + visibleHalfWidth <= viewportWidthPx &&
            placement.centerY - visibleHalfHeight >= 0f &&
            placement.centerY + visibleHalfHeight <= viewportHeightPx
    }

    Box(
        modifier = modifier.requiredSize(viewportWidth, viewportHeight),
        contentAlignment = Alignment.TopStart,
    ) {
        itemPlacements
            .filter { placement ->
                placement.centerX + renderedItemWidthPx / 2f >= 0f &&
                    placement.centerX - renderedItemWidthPx / 2f <= viewportWidthPx &&
                    placement.centerY + renderedItemHeightPx / 2f >= 0f &&
                    placement.centerY - renderedItemHeightPx / 2f <= viewportHeightPx
            }
            .sortedBy { cos(Math.toRadians(it.angle.toDouble())) }
            .forEach { placement ->
                key(placement.index) {
                    itemContent(
                        items[placement.index],
                        Modifier
                            .offset {
                                IntOffset(
                                    (placement.centerX - renderedItemWidthPx / 2f).roundToInt(),
                                    (placement.centerY - renderedItemHeightPx / 2f).roundToInt(),
                                )
                            }
                            .graphicsLayer { rotationZ = placement.angle }
                            .requiredSize(renderedItemWidth, renderedItemHeight),
                    )
                }
            }

        if (almostFullyVisibleCount >= PointerVisibilityCount) {
            val pointerWidth = 30.dp
            val pointerHeight = 22.dp
            val pointerWidthPx = with(density) { pointerWidth.toPx() }
            val pointerHeightPx = with(density) { pointerHeight.toPx() }
            val pointerCenterY = circleCenterY - orbitRadius - renderedItemHeightPx / 2f - pointerHeightPx * .15f
            val pointerColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (circleCenterX - pointerWidthPx / 2f).roundToInt(),
                            (pointerCenterY - pointerHeightPx / 2f).roundToInt(),
                        )
                    }
                    .requiredSize(pointerWidth, pointerHeight),
            ) {
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    },
                    color = pointerColor,
                )
            }
        }
    }
}

private data class PickerItemPlacement(
    val index: Int,
    val angle: Float,
    val centerX: Float,
    val centerY: Float,
)

private fun normalizeAngle(angle: Float): Float = ((angle + 180f) % 360f + 360f) % 360f - 180f

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

private fun optionSpacingRadiusScale(optionCount: Int): Float {
    if (optionCount <= 2) return .7f
    val referenceHalfAngle = Math.PI / ReferenceOptionCount
    val optionHalfAngle = Math.PI / optionCount
    return (sin(referenceHalfAngle) / sin(optionHalfAngle)).toFloat()
}
