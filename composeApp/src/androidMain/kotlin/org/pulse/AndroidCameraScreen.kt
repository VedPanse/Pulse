@file:OptIn(ExperimentalMaterial3Api::class)

package org.pulse

import android.Manifest
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pulse.tracking.DeviceTracker
import org.pulse.tracking.UiDot

private const val WIFI_SCAN_ENABLED = false
private const val PREFS_NAME = "pulse_prefs"
private const val PREFS_HAS_SEEN_INTRO = "has_seen_intro"

@Composable
fun AndroidRootScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    var hasSeenIntro by remember { mutableStateOf(prefs.getBoolean(PREFS_HAS_SEEN_INTRO, false)) }
    if (hasSeenIntro) {
        AndroidCameraScreen()
    } else {
        IntroScreen(
            onStart = {
                prefs.edit().putBoolean(PREFS_HAS_SEEN_INTRO, true).apply()
                hasSeenIntro = true
            },
        )
    }
}

@Composable
private fun IntroScreen(onStart: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "introPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "introPulseScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C0E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val radius = size.minDimension / 3f * pulse
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = radius,
                    center = center,
                    alpha = 0.7f,
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = radius * 1.6f,
                    center = center,
                    alpha = 0.3f,
                    style = Stroke(width = 3f),
                )
            }
            Text(
                text = "Passive signal sensing. No identities.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDDDDDD),
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
            TextButton(
                onClick = onStart,
                modifier = Modifier
                    .height(44.dp)
                    .padding(horizontal = 24.dp)
                    .background(Color.White, RoundedCornerShape(22.dp)),
            ) {
                Text("Start scanning", color = Color.Black)
            }
        }
    }
}

@Composable
private fun AndroidCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker by remember { mutableStateOf(DeviceTracker()) }
    val ingestor = remember { AndroidSignalIngestor(context, tracker, WIFI_SCAN_ENABLED) }
    val dots by tracker.dotsFlow.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions(WIFI_SCAN_ENABLED).toTypedArray())
    }

    DisposableEffect(lifecycleOwner, permissionsGranted) {
        if (permissionsGranted) {
            ingestor.start()
        }
        onDispose {
            ingestor.stop()
        }
    }

    LaunchedEffect(permissionsGranted) {
        while (permissionsGranted) {
            tracker.tick(System.currentTimeMillis())
            delay(200)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(permissionsGranted)
        DotCanvas(tracker = tracker, dots = dots)
        InfoButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 16.dp),
            onClick = { showInfo = true },
        )
    }

    if (showInfo) {
        InfoSheet(
            tracker = tracker,
            sheetState = sheetState,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    showInfo = false
                }
            },
        )
    }
}

@Composable
private fun CameraPreview(permissionsGranted: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    if (!permissionsGranted) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {}
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                bindCamera(context, lifecycleOwner, this)
            }
        }
    )
}

private fun bindCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner, previewView: PreviewView) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
        },
        ContextCompat.getMainExecutor(context),
    )
}

@Composable
private fun DotCanvas(tracker: DeviceTracker, dots: List<UiDot>) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        tracker.setViewport(size.width, size.height)
        dots.forEach { dot ->
            val radius = (6f + 6f * dot.confidence.toFloat()) * pulse
            drawCircle(
                color = Color(0xFFEF4444),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(dot.screenX, dot.screenY),
                alpha = 0.85f,
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = radius * 2f,
                center = androidx.compose.ui.geometry.Offset(dot.screenX, dot.screenY),
                alpha = 0.35f,
                style = Stroke(width = 2f),
            )
        }
    }
}

@Composable
private fun InfoButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color(0x66FFFFFF), CircleShape)
            .border(1.dp, Color(0x88FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(color = Color.White, radius = 2.2f, center = center.copy(y = center.y - 5))
            drawLine(
                color = Color.White,
                start = center.copy(y = center.y - 1),
                end = center.copy(y = center.y + 6),
                strokeWidth = 3f,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
    tracker: DeviceTracker,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    val summary = tracker.getSummarySnapshot()
    val debug = tracker.getDebugSnapshot(System.currentTimeMillis())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "We found ${summary.totalDevices} devices around you.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Confidence: ${summary.confidenceLevel}", color = Color(0xFFE0E0E0))
                Text("Stationary: ${summary.stationaryCount}", color = Color(0xFFE0E0E0))
            }
            Text(
                text = "Passive Bluetooth signals only. No identities stored.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
            )
            Text(
                text = "Tracks: ${debug.totalTracks} • Dots: ${debug.trackableCount}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
            )
            debug.topDevices.forEach { device ->
                Text(
                    text = "${device.keyPrefix}  rssi=${"%.1f".format(device.rssiEma)}  " +
                        "phone=${"%.2f".format(device.phoneScore)}  " +
                        "conf=${"%.2f".format(device.confidence)}  " +
                        "dt=${device.lastSeenDeltaMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                )
            }
            Box(modifier = Modifier.height(12.dp))
        }
    }
}

private fun requiredPermissions(enableWifiScan: Boolean): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (enableWifiScan) {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.ACCESS_WIFI_STATE
        permissions += Manifest.permission.CHANGE_WIFI_STATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
    }
    return permissions.distinct()
}
