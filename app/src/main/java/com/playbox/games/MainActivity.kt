package com.playbox.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.playbox.games.ui.PlayboxApp
import com.playbox.games.ui.theme.PlayboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayboxTheme { PlayboxApp() } }
    }
}
