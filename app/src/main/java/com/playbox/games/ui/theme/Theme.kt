package com.playbox.games.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PlayboxColors = lightColorScheme(
    primary = Color(0xFF6650A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF251047),
    secondary = Color(0xFFFFC94A),
    onSecondary = Color(0xFF342B00),
    tertiary = Color(0xFFE85D87),
    background = Color(0xFFF9F7FC),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFF1ECF4),
    surfaceContainerHigh = Color(0xFFEAE4ED),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF625B66),
    outline = Color(0xFF79747E),
)

private val PlayboxTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val PlayboxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object PlayboxTokens {
    val screenPadding = 24.dp
    val cardPadding = 18.dp
    val itemSpacing = 16.dp
    val primaryButtonHeight = 64.dp
}

@Composable
fun PlayboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlayboxColors, typography = PlayboxTypography, shapes = PlayboxShapes, content = content)
}
