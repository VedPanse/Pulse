package org.pulse

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.pulse.signal.ClusterSnapshot
import org.pulse.signal.SignalEngine

private const val WIFI_SCAN_ENABLED = true

@Composable
fun AndroidCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engineState = remember { mutableStateOf(SignalEngine()) }
    val engine = engineState.value
    val ingestor = remember { AndroidSignalIngestor(context, engine, WIFI_SCAN_ENABLED) }
    val clustersState = remember { mutableStateOf(emptyList<ClusterSnapshot>()) }
    var permissionsGranted by remember { mutableStateOf(false) }

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
            val now = System.currentTimeMillis()
            engine.tick(now)
            clustersState.value = engine.getClustersSnapshot(now)
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(permissionsGranted)
        ClusterDots(clustersState)
        ClusterOverlay(clustersState)
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
        ) {
            Text("Camera & Bluetooth permissions required", color = Color.White)
        }
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
private fun ClusterOverlay(clustersState: MutableState<List<ClusterSnapshot>>) {
    val clusters = clustersState.value
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x88000000)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(clusters) { cluster ->
                ClusterCard(cluster)
            }
        }
    }
}

@Composable
private fun ClusterDots(clustersState: MutableState<List<ClusterSnapshot>>) {
    val clusters = clustersState.value
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
        val padding = 40f
        val width = size.width - padding * 2
        val height = size.height - padding * 2
        clusters.forEach { cluster ->
            val seed = cluster.clusterId.fold(0) { acc, char -> acc * 31 + char.code }
            val xSeed = ((seed and 0xFFFF) % 1000) / 1000f
            val ySeed = (((seed shr 16) and 0xFFFF) % 1000) / 1000f
            val cx = padding + width * xSeed
            val cy = padding + height * ySeed
            val radius = 8f * pulse
            drawCircle(
                color = Color(0xFFEF4444),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                alpha = 0.8f,
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = 18f * pulse,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                alpha = 0.4f,
                style = Stroke(width = 2f),
            )
        }
    }
}

@Composable
private fun ClusterCard(cluster: ClusterSnapshot) {
    val trendLabel = when (cluster.trend) {
        org.pulse.signal.Trend.Strengthening -> "Strengthening"
        org.pulse.signal.Trend.Stable -> "Stable"
        org.pulse.signal.Trend.Weakening -> "Weakening"
    }
    val stability = when {
        cluster.stabilityScore >= 0.7f -> "Stationary"
        cluster.stabilityScore <= 0.3f -> "Moving"
        else -> "Mixed"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101010)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Cluster ${cluster.clusterId}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = "Confidence: ${cluster.confidence} • Trend: $trendLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0E0E0),
            )
            Text(
                text = "Devices: ${cluster.estimatedDeviceCount} • Stability: $stability",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0E0E0),
            )
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
