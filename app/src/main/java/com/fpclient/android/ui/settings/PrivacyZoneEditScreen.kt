package com.fpclient.android.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.util.DeviceLocation
import com.fpclient.android.util.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.roundToInt

/**
 * Full-screen privacy-zone editor. The zone center is always the map's screen center
 * (fixed-crosshair pattern): dragging the map re-places the zone, the radius slider
 * redraws the circle live, and the locate button centers the map on the device's last
 * known GPS fix. `zoneId == null` creates a new zone, otherwise the existing one is
 * loaded and updated (the server requires name + latitude + longitude + radius on PUT).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyZoneEditScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    zoneId: String?,
    onBack: () -> Unit,
) {
    val vm: PrivacyZoneEditViewModel =
        viewModel(factory = PrivacyZoneEditViewModel.factory(container, zoneId, appViewModel))
    val ui by vm.ui.collectAsState()

    val context = LocalContext.current
    var locateTick by remember { mutableStateOf(0) }

    fun locate() {
        val fix = DeviceLocation.lastKnown(context)
        if (fix != null) {
            vm.setLocation(fix.latitude, fix.longitude)
            locateTick += 1
        } else {
            vm.reportNoLocation()
        }
    }

    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        locationGranted = grants.values.any { it }
        if (locationGranted) locate()
    }

    // On first entry: center on where the user is (only when creating — an edited zone
    // seeds its own stored center). Only a cached fix is read; nothing is tracked.
    LaunchedEffect(locationGranted) {
        if (locationGranted && zoneId == null && ui.centerLat == null) locate()
    }

    LaunchedEffect(ui.saved) {
        if (ui.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (zoneId == null) "New privacy zone" else "Edit privacy zone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PrivacyZoneMap(
                    centerLat = ui.centerLat,
                    centerLon = ui.centerLon,
                    radiusMeters = ui.radiusMeters,
                    locateTick = locateTick,
                    onCenterChanged = vm::setCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                FilledTonalIconButton(
                    onClick = {
                        if (locationGranted) locate() else permissionLauncher.launch(LOCATION_PERMISSIONS)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
                }
                if (ui.loadingExisting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (ui.centerLat == null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    ) {
                        Text(
                            "Pan the map or tap the locate button to place the zone center.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = ui.name,
                        onValueChange = vm::setName,
                        label = { Text("Name") },
                        singleLine = true,
                        supportingText = { Text("Up to ${PrivacyZoneEditViewModel.MAX_NAME} characters") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Radius: ${formatRadius(ui.radiusMeters)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = ui.radiusMeters.toFloat(),
                        onValueChange = { vm.setRadius((it / 10).roundToInt() * 10) },
                        valueRange = PrivacyZoneEditViewModel.MIN_RADIUS_M.toFloat()
                            ..PrivacyZoneEditViewModel.MAX_RADIUS_M.toFloat(),
                    )
                    if (ui.error != null) {
                        Text(
                            ui.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onBack) { Text("Cancel") }
                        Button(onClick = vm::save, enabled = ui.canSave) {
                            Text(if (zoneId == null) "Create" else "Save")
                        }
                    }
                }
            }
        }
    }
}

/** Zone placement minimap: the circle + pin always sit at the map's screen center. */
@Composable
private fun PrivacyZoneMap(
    centerLat: Double?,
    centerLon: Double?,
    radiusMeters: Int,
    locateTick: Int,
    onCenterChanged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var cameraPlaced by remember { mutableStateOf(false) }

    // First time a center exists (loaded zone, device fix, or the first manual pan),
    // move the camera there so the user lands on something meaningful.
    LaunchedEffect(centerLat, centerLon, mapView) {
        val lat = centerLat ?: return@LaunchedEffect
        val lon = centerLon ?: return@LaunchedEffect
        val map = mapView ?: return@LaunchedEffect
        if (!cameraPlaced) {
            map.controller.setCenter(GeoPoint(lat, lon))
            cameraPlaced = true
        }
    }

    // Locate button: animate the camera to the freshly-set center.
    LaunchedEffect(locateTick) {
        val lat = centerLat ?: return@LaunchedEffect
        val lon = centerLon ?: return@LaunchedEffect
        if (locateTick > 0) mapView?.controller?.animateTo(GeoPoint(lat, lon))
    }

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.5)
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        // The zone center is the map's screen center — dragging the map
                        // moves the zone. Reported back so Save sends the right place.
                        val center = mapCenter
                        onCenterChanged(center.latitude, center.longitude)
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean = true
                })
            }.also { mapView = it }
        },
        update = { map ->
            map.overlays.removeAll { it is Polygon || it is Marker }
            val lat = centerLat
            val lon = centerLon
            if (lat != null && lon != null) {
                map.overlays.add(
                    Polygon(map).apply {
                        setPoints(
                            GeoMath.circlePoints(lat, lon, radiusMeters.toDouble())
                                .map { GeoPoint(it.first, it.second) },
                        )
                        outlinePaint.color = 0xFFD32F2F.toInt()
                        outlinePaint.strokeWidth = 4f
                        fillPaint.color = 0x26D32F2F
                    },
                )
                Marker(map).apply {
                    position = GeoPoint(lat, lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }.also { map.overlays.add(it) }
            }
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() },
    )
}

class PrivacyZoneEditViewModel(
    private val repository: com.fpclient.android.data.repository.PrivacyZoneRepository,
    @Suppress("unused") private val appViewModel: AppViewModel,
    private val zoneId: String?,
) : ViewModel() {

    data class UiState(
        val loadingExisting: Boolean = false,
        val zoneId: String? = null,
        val name: String = "",
        val centerLat: Double? = null,
        val centerLon: Double? = null,
        val radiusMeters: Int = DEFAULT_RADIUS_M,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val canSave: Boolean
            get() = !loadingExisting && !saving && name.isNotBlank() &&
                centerLat != null && centerLon != null
    }

    private val _ui = MutableStateFlow(UiState(loadingExisting = zoneId != null, zoneId = zoneId))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        if (zoneId != null) {
            viewModelScope.launch {
                when (val r = repository.list()) {
                    is com.fpclient.android.data.network.ApiResult.Success -> {
                        val zone = r.data.firstOrNull { it.id == zoneId }
                        _ui.value = if (zone != null) {
                            _ui.value.copy(
                                loadingExisting = false,
                                name = zone.name ?: "",
                                centerLat = zone.latitude,
                                centerLon = zone.longitude,
                                radiusMeters = (zone.radiusMeters ?: DEFAULT_RADIUS_M)
                                    .coerceIn(MIN_RADIUS_M, MAX_RADIUS_M),
                            )
                        } else {
                            _ui.value.copy(loadingExisting = false, error = "Privacy zone not found")
                        }
                    }
                    is com.fpclient.android.data.network.ApiResult.Error -> _ui.value =
                        _ui.value.copy(loadingExisting = false, error = r.message)
                }
            }
        }
    }

    fun setName(value: String) {
        // Server contract: name is 1..100 characters.
        _ui.value = _ui.value.copy(name = value.take(MAX_NAME))
    }

    /** Called by the map while the user drags — the screen center is the zone center. */
    fun setCenter(latitude: Double, longitude: Double) {
        _ui.value = _ui.value.copy(centerLat = latitude, centerLon = longitude)
    }

    fun setRadius(meters: Int) {
        _ui.value = _ui.value.copy(radiusMeters = meters.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M))
    }

    /** Locate button / first-entry seeding: jump the zone to the device's cached fix. */
    fun setLocation(latitude: Double, longitude: Double) {
        setCenter(latitude, longitude)
    }

    fun reportNoLocation() {
        _ui.value = _ui.value.copy(error = "No recent location found — pan the map to place the zone.")
    }

    fun save() {
        val state = _ui.value
        val lat = state.centerLat ?: return
        val lon = state.centerLon ?: return
        val id = state.zoneId
        viewModelScope.launch {
            _ui.value = _ui.value.copy(saving = true, error = null)
            val result = if (id != null) {
                repository.update(id, state.name.trim(), lat, lon, state.radiusMeters)
            } else {
                repository.create(state.name.trim(), lat, lon, state.radiusMeters)
            }
            when (result) {
                is com.fpclient.android.data.network.ApiResult.Success ->
                    _ui.value = _ui.value.copy(saving = false, saved = true)
                is com.fpclient.android.data.network.ApiResult.Error ->
                    _ui.value = _ui.value.copy(saving = false, error = result.message)
            }
        }
    }

    companion object {
        /** Server contract (CreatePrivacyZoneRequest / UpdatePrivacyZoneRequest validation). */
        const val MIN_RADIUS_M = 50
        const val MAX_RADIUS_M = 10_000
        const val DEFAULT_RADIUS_M = 500
        const val MAX_NAME = 100

        fun factory(
            container: AppContainer,
            zoneId: String?,
            appViewModel: AppViewModel,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PrivacyZoneEditViewModel(container.privacyZoneRepository, appViewModel, zoneId) }
        }
    }
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun formatRadius(meters: Int): String =
    if (meters < 1000) "$meters m" else "${meters / 1000.0} km"
