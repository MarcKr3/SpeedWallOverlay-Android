package com.speedwall.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.speedwall.overlay.sensor.MotionManager
import com.speedwall.overlay.state.AppState
import com.speedwall.overlay.ui.ContentScreen
import com.speedwall.overlay.ui.theme.SpeedWallOverlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeedWallOverlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpeedWallApp()
                }
            }
        }
    }
}

@Composable
fun SpeedWallApp() {
    val appState: AppState = viewModel()
    val context = LocalContext.current
    val motionManager = remember { MotionManager(context) }

    DisposableEffect(Unit) {
        onDispose { motionManager.stop() }
    }

    ContentScreen(appState = appState, motionManager = motionManager)
}
