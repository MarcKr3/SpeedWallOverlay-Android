package com.speedwall.overlay.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.sqrt

data class CalibrationPoint(
    var screenPosition: Offset,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AppMode {
    data object Calibration : AppMode
    data object Overlay : AppMode
}

enum class DistanceUnit(val symbol: String) {
    METERS("m"),
    CENTIMETERS("cm"),
    INCHES("in"),
    FEET("ft");

    fun toMeters(value: Double): Double = when (this) {
        METERS -> value
        CENTIMETERS -> value / 100.0
        INCHES -> value * 0.0254
        FEET -> value * 0.3048
    }
}

sealed interface CalibrationState {
    data object WaitingForFirstPoint : CalibrationState
    data class WaitingForSecondPoint(val firstPoint: CalibrationPoint) : CalibrationState
    data class WaitingForDistance(val firstPoint: CalibrationPoint, val secondPoint: CalibrationPoint) : CalibrationState
    data object Complete : CalibrationState
}

class AppState : ViewModel() {

    private val _mode = MutableStateFlow<AppMode>(AppMode.Calibration)
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.WaitingForFirstPoint)
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private val _knownDistanceMeters = MutableStateFlow(1.0)
    val knownDistanceMeters: StateFlow<Double> = _knownDistanceMeters.asStateFlow()

    private val _distanceInputText = MutableStateFlow("1.0")
    val distanceInputText: StateFlow<String> = _distanceInputText.asStateFlow()

    private val _selectedDistanceUnit = MutableStateFlow(defaultUnit())
    val selectedDistanceUnit: StateFlow<DistanceUnit> = _selectedDistanceUnit.asStateFlow()

    private val _pixelsPerMeter = MutableStateFlow(0f)
    val pixelsPerMeter: StateFlow<Float> = _pixelsPerMeter.asStateFlow()

    private val _screenWidth = MutableStateFlow(0f)
    val screenWidth: StateFlow<Float> = _screenWidth.asStateFlow()

    private val _screenHeight = MutableStateFlow(0f)
    val screenHeight: StateFlow<Float> = _screenHeight.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _showLabels = MutableStateFlow(false)
    val showLabels: StateFlow<Boolean> = _showLabels.asStateFlow()

    private val _overlayColor = MutableStateFlow(Color.Black)
    val overlayColor: StateFlow<Color> = _overlayColor.asStateFlow()

    private val _horizontalTilt = MutableStateFlow(0.0)
    val horizontalTilt: StateFlow<Double> = _horizontalTilt.asStateFlow()

    private val _verticalTilt = MutableStateFlow(0.0)
    val verticalTilt: StateFlow<Double> = _verticalTilt.asStateFlow()

    private val _autoLevel = MutableStateFlow(false)
    val autoLevel: StateFlow<Boolean> = _autoLevel.asStateFlow()

    // Internal calibration points (not exposed as flow — derived via calibrationPoints)
    private var firstCalibrationPoint: CalibrationPoint? = null
    private var secondCalibrationPoint: CalibrationPoint? = null

    // Derived: list of calibration point positions
    private val _calibrationPoints = MutableStateFlow<List<Offset>>(emptyList())
    val calibrationPoints: StateFlow<List<Offset>> = _calibrationPoints.asStateFlow()

    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated.asStateFlow()

    // --- Setters ---

    fun updateDistanceInputText(text: String) { _distanceInputText.value = text }
    fun updateSelectedDistanceUnit(unit: DistanceUnit) { _selectedDistanceUnit.value = unit }
    fun updateScreenSize(w: Float, h: Float) { _screenWidth.value = w; _screenHeight.value = h }
    fun setShowGrid(v: Boolean) { _showGrid.value = v }
    fun setShowLabels(v: Boolean) { _showLabels.value = v }
    fun setOverlayColor(c: Color) { _overlayColor.value = c }
    fun setHorizontalTilt(v: Double) { _horizontalTilt.value = v }
    fun setVerticalTilt(v: Double) { _verticalTilt.value = v }
    fun setAutoLevel(v: Boolean) { _autoLevel.value = v }

    // --- Calibration logic ---

    fun recordCalibrationTap(at: Offset) {
        when (_calibrationState.value) {
            is CalibrationState.WaitingForFirstPoint -> {
                val point = CalibrationPoint(screenPosition = at)
                firstCalibrationPoint = point
                _calibrationState.value = CalibrationState.WaitingForSecondPoint(firstPoint = point)
            }
            is CalibrationState.WaitingForSecondPoint -> {
                val point = CalibrationPoint(screenPosition = at)
                secondCalibrationPoint = point
                val first = firstCalibrationPoint!!
                _calibrationState.value = CalibrationState.WaitingForDistance(firstPoint = first, secondPoint = point)
            }
            else -> {}
        }
        refreshDerived()
    }

    fun setKnownDistance(meters: Double) {
        val first = firstCalibrationPoint ?: return
        val second = secondCalibrationPoint ?: return
        _knownDistanceMeters.value = meters

        val dx = (second.screenPosition.x - first.screenPosition.x).toDouble()
        val dy = (second.screenPosition.y - first.screenPosition.y).toDouble()
        val pixelDistance = sqrt(dx * dx + dy * dy).toFloat()
        _pixelsPerMeter.value = pixelDistance / meters.toFloat()

        _calibrationState.value = CalibrationState.Complete
        refreshDerived()
    }

    fun updatePointPosition(index: Int, newPosition: Offset) {
        if (_calibrationState.value !is CalibrationState.Complete) return
        if (index == 0) {
            firstCalibrationPoint = firstCalibrationPoint?.copy(screenPosition = newPosition)
        } else {
            secondCalibrationPoint = secondCalibrationPoint?.copy(screenPosition = newPosition)
        }
        val first = firstCalibrationPoint ?: return
        val second = secondCalibrationPoint ?: return
        val dist = _knownDistanceMeters.value
        if (dist <= 0.0) return

        val dx = (second.screenPosition.x - first.screenPosition.x).toDouble()
        val dy = (second.screenPosition.y - first.screenPosition.y).toDouble()
        val pixelDistance = sqrt(dx * dx + dy * dy).toFloat()
        _pixelsPerMeter.value = pixelDistance / dist.toFloat()
        refreshDerived()
    }

    fun resetCalibration() {
        _calibrationState.value = CalibrationState.WaitingForFirstPoint
        firstCalibrationPoint = null
        secondCalibrationPoint = null
        _pixelsPerMeter.value = 0f
        refreshDerived()
    }

    fun proceedToOverlay() {
        if (_calibrationState.value !is CalibrationState.Complete) return
        _mode.value = AppMode.Overlay
    }

    fun backToCalibration() {
        _mode.value = AppMode.Calibration
    }

    private fun refreshDerived() {
        val points = mutableListOf<Offset>()
        firstCalibrationPoint?.let { points.add(it.screenPosition) }
        secondCalibrationPoint?.let { points.add(it.screenPosition) }
        _calibrationPoints.value = points
        _isCalibrated.value = _calibrationState.value is CalibrationState.Complete && _pixelsPerMeter.value > 0f
    }

    companion object {
        private fun defaultUnit(): DistanceUnit {
            val country = Locale.getDefault().country.uppercase()
            return if (country in setOf("US", "LR", "MM")) DistanceUnit.FEET else DistanceUnit.METERS
        }
    }
}
