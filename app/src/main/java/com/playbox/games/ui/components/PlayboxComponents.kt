package com.playbox.games.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayboxScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (title.isNotEmpty()) {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            subtitle?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Text("‹", fontSize = 38.sp, fontWeight = FontWeight.Light)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun PlayboxBackground(dark: Boolean = false, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        if (dark) view.keepScreenOn = true
        onDispose {
            if (dark) view.keepScreenOn = false
        }
    }

    val colorScheme = if (dark) {
        MaterialTheme.colorScheme.copy(
            primary = Color(0xFFD0BCFF),
            background = Color(0xFF303034),
            onBackground = Color(0xFFF5F3F7),
            surface = Color(0xFF303034),
            onSurface = Color(0xFFF5F3F7),
            onSurfaceVariant = Color(0xFFD0CBD3),
            outline = Color(0xFF938F99),
        )
    } else {
        MaterialTheme.colorScheme
    }

    // The window is drawn edge to edge, so the system bars show this background colour.
    // The icons only need their tint flipped; the navigation bar also needs an explicit
    // colour, because 3-button navigation scrims a transparent one into its own shade.
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = colorScheme.background.toArgb()
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            Modifier.fillMaxSize()
                .background(colorScheme.background),
        ) { content() }
    }
}

enum class ToolIcon { Dice, Rabbit, Arithmetic }

@Composable
fun ToolCard(icon: ToolIcon, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    val cardColor = accent.copy(alpha = .1f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(194.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ToolIconArtwork(icon = icon, accent = accent)
                Text(
                    "›",
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = accent,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolIconArtwork(icon: ToolIcon, accent: Color) {
    Box(
        modifier = Modifier
            .size(98.dp)
            .background(accent.copy(alpha = .17f), RoundedCornerShape(29.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (icon) {
            ToolIcon.Dice -> DiceToolIcon(accent)
            ToolIcon.Rabbit -> RabbitToolIcon(accent)
            ToolIcon.Arithmetic -> ArithmeticToolIcon(accent)
        }
    }
}

@Composable
private fun DiceToolIcon(accent: Color) {
    Surface(
        modifier = Modifier
            .size(68.dp)
            .graphicsLayer { rotationZ = -8f },
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val radius = size.minDimension * .105f
            val points = listOf(
                .2f to .2f,
                .8f to .2f,
                .5f to .5f,
                .2f to .8f,
                .8f to .8f,
            )
            points.forEach { (x, y) ->
                drawCircle(accent, radius, androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
            }
        }
    }
}

@Composable
private fun RabbitToolIcon(accent: Color) {
    Canvas(Modifier.size(76.dp)) {
        val outline = Color(0xFF234137)
        drawOval(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width * .2f, 0f), size = androidx.compose.ui.geometry.Size(size.width * .22f, size.height * .5f))
        drawOval(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width * .58f, 0f), size = androidx.compose.ui.geometry.Size(size.width * .22f, size.height * .5f))
        drawOval(accent.copy(alpha = .38f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .07f), size = androidx.compose.ui.geometry.Size(size.width * .12f, size.height * .32f))
        drawOval(accent.copy(alpha = .38f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .63f, size.height * .07f), size = androidx.compose.ui.geometry.Size(size.width * .12f, size.height * .32f))
        drawCircle(Color.White, radius = size.width * .34f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .59f))
        drawCircle(outline, radius = size.width * .035f, center = androidx.compose.ui.geometry.Offset(size.width * .39f, size.height * .55f))
        drawCircle(outline, radius = size.width * .035f, center = androidx.compose.ui.geometry.Offset(size.width * .61f, size.height * .55f))
        drawCircle(accent, radius = size.width * .055f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .67f))
        drawArc(outline, 15f, 150f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .37f, size.height * .64f), size = androidx.compose.ui.geometry.Size(size.width * .26f, size.height * .18f), style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun ArithmeticToolIcon(accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(listOf("+", "−"), listOf("×", "÷")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { symbol ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(symbol, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
