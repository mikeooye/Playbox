package com.playbox.games.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.playbox.games.ui.components.PlayboxBackground
import com.playbox.games.ui.components.PlayboxScaffold
import com.playbox.games.ui.components.ToolCard
import com.playbox.games.ui.components.ToolIcon

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val icon: ToolIcon,
    val accent: Color,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(onOpenDice: () -> Unit, onOpenRabbitTrap: () -> Unit, onOpenArithmetic: () -> Unit) {
    PlayboxBackground {
        PlayboxScaffold(title = "欢乐工具箱") { contentModifier ->
            val tools = listOf(
                HomeTool("幸运骰子", "摇出 1–6", ToolIcon.Dice, MaterialTheme.colorScheme.secondary, onOpenDice),
                HomeTool("兔子陷阱棋", "抽取行动卡", ToolIcon.Rabbit, Color(0xFF38A875), onOpenRabbitTrap),
                HomeTool("四则运算", "逐题练习", ToolIcon.Arithmetic, Color(0xFF5B67D8), onOpenArithmetic),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                items(tools) { tool ->
                    ToolCard(
                        icon = tool.icon,
                        title = tool.title,
                        subtitle = tool.subtitle,
                        accent = tool.accent,
                        onClick = tool.onClick,
                    )
                }
            }
        }
    }
}
