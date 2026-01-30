package com.speedwall.overlay.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedwall.overlay.R
import com.speedwall.overlay.camera.CameraManager
import com.speedwall.overlay.sensor.MotionManager
import com.speedwall.overlay.state.AppMode
import com.speedwall.overlay.state.AppState
import kotlinx.coroutines.delay
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

// Preset color palette (replacement for iOS ColorPicker)
private val presetColors = listOf(
    Color.Black, Color.White, Color.Red, Color(0xFF4CAF50),
    Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta,
    Color(0xFFFF9800), Color(0xFFE91E63)
)

@Composable
fun OverlayScreen(
    appState: AppState,
    motionManager: MotionManager,
    cameraManager: CameraManager
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val mode by appState.mode.collectAsState()
    val pixelsPerMeter by appState.pixelsPerMeter.collectAsState()
    val showGrid by appState.showGrid.collectAsState()
    val showLabels by appState.showLabels.collectAsState()
    val overlayColor by appState.overlayColor.collectAsState()
    val horizontalTilt by appState.horizontalTilt.collectAsState()
    val verticalTilt by appState.verticalTilt.collectAsState()
    val autoLevel by appState.autoLevel.collectAsState()
    val rollCorrection by motionManager.rollCorrectionDegrees.collectAsState()

    val wallWidthMeters = 3.0f
    val wallHeightMeters = 15.0f
    val renderedWidth = wallWidthMeters * pixelsPerMeter
    val renderedHeight = wallHeightMeters * pixelsPerMeter

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showFlash by remember { mutableStateOf(false) }
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Start/stop motion manager based on mode and autoLevel
    LaunchedEffect(mode, autoLevel) {
        if (mode is AppMode.Overlay && autoLevel) {
            motionManager.start()
        } else {
            motionManager.stop()
        }
    }

    // Set initial position when entering overlay mode
    LaunchedEffect(mode) {
        if (mode is AppMode.Overlay) {
            offsetY = screenHeight / 3f - renderedHeight / 2f
        }
    }

    fun clampOffset() {
        val margin = 100f
        val maxX = (screenWidth + renderedWidth) / 2f - margin
        offsetX = offsetX.coerceIn(-maxX, maxX)
        val maxY = (screenHeight + renderedHeight) / 2f - margin
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { showControls = !showControls }
                )
            }
    ) {
        // Overlay layers with transforms
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y
                        },
                        onDragEnd = {
                            offsetX += dragOffsetX
                            offsetY += dragOffsetY
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            clampOffset()
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = offsetX + dragOffsetX
                        translationY = offsetY + dragOffsetY
                        rotationZ = if (autoLevel) rollCorrection else 0f
                        rotationX = verticalTilt.toFloat()
                        rotationY = horizontalTilt.toFloat()
                        cameraDistance = 12f * density.density
                    }
            ) {
                // Overlay image (always visible)
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.overlay),
                    contentDescription = "Speed wall overlay",
                    colorFilter = ColorFilter.tint(overlayColor),
                    modifier = Modifier
                        .width(with(density) { renderedWidth.toDp() })
                        .height(with(density) { renderedHeight.toDp() })
                )

                // Grid layer
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.grid),
                    contentDescription = "Grid overlay",
                    colorFilter = ColorFilter.tint(overlayColor),
                    modifier = Modifier
                        .width(with(density) { renderedWidth.toDp() })
                        .height(with(density) { renderedHeight.toDp() })
                        .alpha(if (showGrid) 1f else 0.01f)
                )

                // Labels layer
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.labels),
                    contentDescription = "Labels overlay",
                    colorFilter = ColorFilter.tint(overlayColor),
                    modifier = Modifier
                        .width(with(density) { renderedWidth.toDp() })
                        .height(with(density) { renderedHeight.toDp() })
                        .alpha(if (showLabels) 1f else 0.01f)
                )
            }
        }

        // Flash effect for screenshot
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Controls overlay
        if (showControls) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = { appState.backToCalibration() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to calibration",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    // Color picker button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable { showColorPicker = !showColorPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(overlayColor)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }

                    // Grid toggle
                    IconButton(
                        onClick = { appState.setShowGrid(!showGrid) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (showGrid) Color.Yellow else Color.Black.copy(alpha = 0.3f)
                            )
                    ) {
                        Icon(
                            Icons.Filled.GridOn,
                            contentDescription = if (showGrid) "Hide grid" else "Show grid",
                            tint = if (showGrid) Color.Black else Color.White
                        )
                    }

                    // Labels toggle
                    IconButton(
                        onClick = { appState.setShowLabels(!showLabels) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (showLabels) Color.Yellow else Color.Black.copy(alpha = 0.3f)
                            )
                    ) {
                        Icon(
                            Icons.Filled.Straighten,
                            contentDescription = if (showLabels) "Hide labels" else "Show labels",
                            tint = if (showLabels) Color.Black else Color.White
                        )
                    }
                }
            }

            // Color picker row (shown below top bar)
            if (showColorPicker) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    2.dp,
                                    if (overlayColor == color) Color.Yellow else Color.Gray,
                                    CircleShape
                                )
                                .clickable {
                                    appState.setOverlayColor(color)
                                    showColorPicker = false
                                }
                        )
                    }
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(16.dp)
            ) {
                // Screenshot + Auto-level row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Screenshot button
                    Box(
                        modifier = Modifier
                            .size(35.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable {
                                takeScreenshot(context, cameraManager, showControls) { flash ->
                                    showFlash = flash
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    // Auto-level toggle
                    Box(
                        modifier = Modifier
                            .size(35.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                if (autoLevel) Color.Yellow else Color.White,
                                CircleShape
                            )
                            .clickable { appState.setAutoLevel(!autoLevel) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "L",
                            color = if (autoLevel) Color.Yellow else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal tilt slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text("↔", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.width(24.dp))
                    Slider(
                        value = horizontalTilt.toFloat(),
                        onValueChange = { appState.setHorizontalTilt(it.toDouble()) },
                        valueRange = -45f..45f,
                        steps = 179,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Yellow,
                            activeTrackColor = Color.Yellow
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { appState.setHorizontalTilt(0.0) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("↺", color = Color.White, fontSize = 14.sp)
                    }
                }

                // Vertical tilt slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text("↕", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.width(24.dp))
                    Slider(
                        value = verticalTilt.toFloat(),
                        onValueChange = { appState.setVerticalTilt(it.toDouble()) },
                        valueRange = -45f..45f,
                        steps = 179,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Yellow,
                            activeTrackColor = Color.Yellow
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { appState.setVerticalTilt(0.0) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("↺", color = Color.White, fontSize = 14.sp)
                    }
                }

                Text(
                    "Double-tap to hide controls",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}

private fun takeScreenshot(
    context: Context,
    cameraManager: CameraManager,
    showControls: Boolean,
    onFlash: (Boolean) -> Unit
) {
    val frame = cameraManager.latestFrame ?: return

    // Create a composite bitmap
    val bitmap = frame.copy(Bitmap.Config.ARGB_8888, true)

    // Save to gallery using MediaStore
    val filename = "SpeedWall_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SpeedWall")
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        var outputStream: OutputStream? = null
        try {
            outputStream = resolver.openOutputStream(uri)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream!!)
            Toast.makeText(context, "Screenshot saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Screenshot failed", Toast.LENGTH_SHORT).show()
        } finally {
            outputStream?.close()
        }
    }

    bitmap.recycle()

    // Flash effect
    onFlash(true)
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        onFlash(false)
    }, 150)
}
