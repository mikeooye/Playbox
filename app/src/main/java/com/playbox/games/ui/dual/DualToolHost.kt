package com.playbox.games.ui.dual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.playbox.games.ui.ToolKind
import com.playbox.games.ui.arithmetic.ArithmeticScreen
import com.playbox.games.ui.components.LandscapeOrientationLock
import com.playbox.games.ui.dice.DiceScreen
import com.playbox.games.ui.rabbittrap.RabbitTrapScreen

@Composable
fun DualToolHost(left: ToolKind, right: ToolKind, onBack: () -> Unit) {
    LandscapeOrientationLock()
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxHeight()) { CompactTool(left, onBack = onBack) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = .45f)))
        Box(Modifier.weight(1f).fillMaxHeight()) { CompactTool(right, onBack = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToolSheet(current: ToolKind, onDismiss: () -> Unit, onSelect: (ToolKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("添加第二个工具", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "${current.title} 将放在左侧，新工具放在右侧。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            ToolKind.entries.filter { it.supportsDualMode }.forEach { tool ->
                Surface(
                    modifier = Modifier.fillMaxWidth().height(66.dp).clickable { onSelect(tool) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(tool.emoji, style = MaterialTheme.typography.headlineMedium)
                        Text(tool.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        if (tool == current) Text("再开一个", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CompactTool(tool: ToolKind, onBack: (() -> Unit)?) {
    when (tool) {
        ToolKind.Dice -> DiceScreen(onBack = onBack, compact = true)
        ToolKind.RabbitTrap -> RabbitTrapScreen(onBack = onBack, compact = true)
        ToolKind.Arithmetic -> ArithmeticScreen(onBack = onBack, compact = true)
    }
}
