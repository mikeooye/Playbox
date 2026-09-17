package com.playbox.games.ui

enum class ToolKind(val title: String, val emoji: String, val supportsDualMode: Boolean = true) {
    Dice("幸运骰子", "🎲"),
    RabbitTrap("兔子陷阱棋", "🐇"),
    Arithmetic("四则运算", "➗", supportsDualMode = false),
}
