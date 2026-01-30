package com.speedwall.overlay.ui

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.speedwall.overlay.camera.CameraManager
import com.speedwall.overlay.sensor.MotionManager
import com.speedwall.overlay.state.AppMode
import com.speedwall.overlay.state.AppState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ContentScreen(appState: AppState, motionManager: MotionManager) {
    val mode by appState.mode.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val cameraManager = remember { CameraManager(context) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                with(density) {
                    appState.updateScreenSize(
                        size.width.toDp().value,
                        size.height.toDp().value
                    )
                }
            }
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Camera preview (always visible, bottom layer)
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            DisposableEffect(lifecycleOwner) {
                cameraManager.bindCamera(lifecycleOwner, previewView)
                onDispose { cameraManager.shutdown() }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Calibration screen
            AnimatedVisibility(
                visible = mode is AppMode.Calibration,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CalibrationScreen(appState = appState)
            }

            // Overlay screen
            AnimatedVisibility(
                visible = mode is AppMode.Overlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OverlayScreen(
                    appState = appState,
                    motionManager = motionManager,
                    cameraManager = cameraManager
                )
            }
        } else {
            // Permission not granted
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (cameraPermissionState.status.shouldShowRationale) {
                            "SpeedWall Overlay needs camera access to display the climbing wall overlay."
                        } else {
                            "Camera permission was denied. Please enable it in Settings."
                        },
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
