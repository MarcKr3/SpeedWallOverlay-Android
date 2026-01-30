package com.speedwall.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedwall.overlay.state.AppState
import com.speedwall.overlay.state.CalibrationState
import com.speedwall.overlay.state.DistanceUnit
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(appState: AppState) {
    val calibrationState by appState.calibrationState.collectAsState()
    val calibrationPoints by appState.calibrationPoints.collectAsState()
    val isCalibrated by appState.isCalibrated.collectAsState()
    val pixelsPerMeter by appState.pixelsPerMeter.collectAsState()
    val distanceInputText by appState.distanceInputText.collectAsState()
    val selectedDistanceUnit by appState.selectedDistanceUnit.collectAsState()

    var showDistanceInput by remember { mutableStateOf(false) }
    var draggingPointIndex by remember { mutableStateOf<Int?>(null) }
    var pointDragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingLine by remember { mutableStateOf(false) }
    var lineDragOffset by remember { mutableStateOf(Offset.Zero) }
    var showCompleteBanner by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Show complete banner briefly
    LaunchedEffect(calibrationState) {
        if (calibrationState is CalibrationState.Complete) {
            showCompleteBanner = true
            delay(1500)
            showCompleteBanner = false
        } else {
            showCompleteBanner = false
        }
    }

    // Show distance input when entering WaitingForDistance state
    LaunchedEffect(calibrationState) {
        if (calibrationState is CalibrationState.WaitingForDistance) {
            showDistanceInput = true
        }
    }

    fun displayPosition(index: Int, basePoint: Offset): Offset {
        var point = basePoint
        if (index == draggingPointIndex) {
            point = Offset(point.x + pointDragOffset.x, point.y + pointDragOffset.y)
        }
        if (draggingLine) {
            point = Offset(point.x + lineDragOffset.x, point.y + lineDragOffset.y)
        }
        return point
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Tap gesture layer
        if (calibrationState !is CalibrationState.Complete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(calibrationState) {
                        detectTapGestures { offset ->
                            when (calibrationState) {
                                is CalibrationState.WaitingForFirstPoint,
                                is CalibrationState.WaitingForSecondPoint -> {
                                    appState.recordCalibrationTap(offset)
                                }
                                else -> {}
                            }
                        }
                    }
            )
        }

        // Dashed line between calibration points
        if (calibrationPoints.size == 2) {
            val p1 = displayPosition(0, calibrationPoints[0])
            val p2 = displayPosition(1, calibrationPoints[1])
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color.Yellow,
                    start = p1,
                    end = p2,
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                )
            }
        }

        // Distance label at midpoint
        if (calibrationState is CalibrationState.Complete && calibrationPoints.size == 2) {
            val p1 = displayPosition(0, calibrationPoints[0])
            val p2 = displayPosition(1, calibrationPoints[1])
            val midX = (p1.x + p2.x) / 2f
            val midY = (p1.y + p2.y) / 2f
            val angleDeg = Math.toDegrees(
                atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
            ).toFloat()
            val correctedAngle = if (angleDeg > 90f || angleDeg < -90f) angleDeg + 180f else angleDeg
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (midX - with(density) { 40.dp.toPx() }).roundToInt(),
                            (midY - with(density) { 14.dp.toPx() }).roundToInt()
                        )
                    }
                    .rotate(correctedAngle)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Yellow)
                    .clickable { showDistanceInput = true }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { draggingLine = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                lineDragOffset = Offset(
                                    lineDragOffset.x + dragAmount.x,
                                    lineDragOffset.y + dragAmount.y
                                )
                            },
                            onDragEnd = {
                                for (i in calibrationPoints.indices) {
                                    val old = calibrationPoints[i]
                                    appState.updatePointPosition(
                                        i, Offset(old.x + lineDragOffset.x, old.y + lineDragOffset.y)
                                    )
                                }
                                draggingLine = false
                                lineDragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                draggingLine = false
                                lineDragOffset = Offset.Zero
                            }
                        )
                    }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$distanceInputText ${selectedDistanceUnit.symbol}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Calibration point markers
        calibrationPoints.forEachIndexed { index, point ->
            val displayPos = displayPosition(index, point)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (displayPos.x - 12.dp.toPx()).roundToInt(),
                            (displayPos.y - 12.dp.toPx()).roundToInt()
                        )
                    }
                    .size(24.dp)
                    .then(
                        if (calibrationState is CalibrationState.Complete) {
                            Modifier.pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = { draggingPointIndex = index },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        pointDragOffset = Offset(
                                            pointDragOffset.x + dragAmount.x,
                                            pointDragOffset.y + dragAmount.y
                                        )
                                    },
                                    onDragEnd = {
                                        appState.updatePointPosition(
                                            index,
                                            Offset(point.x + pointDragOffset.x, point.y + pointDragOffset.y)
                                        )
                                        draggingPointIndex = null
                                        pointDragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingPointIndex = null
                                        pointDragOffset = Offset.Zero
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                CalibrationPointMarker(number = index + 1)
            }
        }

        // Top instruction banner
        AnimatedVisibility(
            visible = calibrationState !is CalibrationState.Complete || showCompleteBanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Text(
                text = when (calibrationState) {
                    is CalibrationState.WaitingForFirstPoint -> "Tap first point of known distance"
                    is CalibrationState.WaitingForSecondPoint -> "Tap second point"
                    is CalibrationState.WaitingForDistance -> "Enter the distance between points"
                    is CalibrationState.Complete -> "Calibration complete!"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // px/m display
            AnimatedVisibility(
                visible = calibrationState is CalibrationState.Complete,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Text(
                    text = String.format("%.1f px/m", pixelsPerMeter),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showDistanceInput = true }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Reset button
                AnimatedVisibility(
                    visible = calibrationState !is CalibrationState.WaitingForFirstPoint,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    OutlinedButton(
                        onClick = { appState.resetCalibration() },
                        shape = RoundedCornerShape(25.dp),
                        modifier = Modifier.widthIn(min = 130.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                }

                // Continue button
                AnimatedVisibility(
                    visible = isCalibrated,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Button(
                        onClick = { appState.proceedToOverlay() },
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.widthIn(min = 130.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Info button
            if (calibrationState is CalibrationState.WaitingForFirstPoint && !showAbout) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = { showAbout = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Text("i", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic, color = Color.White)
                    }
                }
            }
        }

        // About dialog
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("SpeedWall Overlay", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "1. Calibrate to a known distance\n\n2. Speed-Route Overlay for easy setup.",
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        Text("Version 1.0", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(onClick = { showAbout = false }) { Text("Close") }
                }
            )
        }

        // Distance input dialog
        if (showDistanceInput) {
            DistanceInputDialog(
                distanceInput = distanceInputText,
                selectedUnit = selectedDistanceUnit,
                onDistanceChange = { appState.updateDistanceInputText(it) },
                onUnitChange = { appState.updateSelectedDistanceUnit(it) },
                onConfirm = {
                    val value = distanceInputText.toDoubleOrNull()
                    if (value != null && value > 0) {
                        appState.setKnownDistance(selectedDistanceUnit.toMeters(value))
                        showDistanceInput = false
                    }
                },
                onCancel = {
                    if (calibrationState is CalibrationState.WaitingForDistance) {
                        appState.setKnownDistance(1.0)
                    }
                    showDistanceInput = false
                }
            )
        }
    }
}

@Composable
private fun CalibrationPointMarker(number: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val c = center
            drawCircle(Color.Yellow.copy(alpha = 0.3f), radius = 11.dp.toPx(), center = c)
            drawCircle(Color.Yellow, radius = 12.5f.dp.toPx(), center = c,
                style = Stroke(width = 1.5f.dp.toPx()))
            drawLine(Color.Yellow, Offset(c.x, c.y - 5.dp.toPx()),
                Offset(c.x, c.y + 5.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawLine(Color.Yellow, Offset(c.x - 5.dp.toPx(), c.y),
                Offset(c.x + 5.dp.toPx(), c.y), strokeWidth = 1.dp.toPx())
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.Yellow)
        ) {
            Text("$number", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
private fun DistanceInputDialog(
    distanceInput: String,
    selectedUnit: DistanceUnit,
    onDistanceChange: (String) -> Unit,
    onUnitChange: (DistanceUnit) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Enter Known Distance", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = onDistanceChange,
                    label = { Text("Distance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(150.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DistanceUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { onUnitChange(unit) },
                            label = { Text(unit.symbol) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.Red) }
        }
    )
}
