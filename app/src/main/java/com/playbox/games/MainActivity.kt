package com.playbox.games

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.playbox.games.ui.PlayboxApp
import com.playbox.games.ui.theme.PlayboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge to edge: the page background continues behind the system bars, so the
        // bars never show a colour of their own. Per-screen icon tinting happens in
        // PlayboxBackground, which knows how light or dark the current page is.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7.x can only draw white navigation icons, so that bar stays opaque dark;
            // from Android 8 the page colour is applied per screen in PlayboxBackground.
            window.navigationBarColor = Color.parseColor("#1D1B20")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No system scrim on top of the page background.
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent { PlayboxTheme { PlayboxApp() } }
    }
}
