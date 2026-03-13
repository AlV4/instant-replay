package com.instantreplay.app.ui

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.instantreplay.app.state.AppMode
import com.instantreplay.app.state.AppStateManager
import com.instantreplay.app.state.BufferStats
import com.instantreplay.app.state.CameraPermissionState
import com.instantreplay.app.state.ExportState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InstantReplayApp(
    viewModel: AppStateManager = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Initialize managers
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Update permission state in viewmodel
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        viewModel.updatePermissionState(cameraPermissionState.status.isGranted)
    }
    
    // Collect states
    val currentMode by viewModel.currentMode.collectAsState()
    val permissionState by viewModel.cameraPermissionState.collectAsState()
    val bufferStats by viewModel.bufferStats.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    
    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onBackground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !cameraPermissionState.status.isGranted -> {
                // Permission request screen
                PermissionScreen(
                    shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
            else -> {
                // Camera preview
                CameraPreview(
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner
                )
                
                // Mode overlay
                when (currentMode) {
                    AppMode.PREVIEW -> PreviewModeOverlay(
                        onStartLive = { viewModel.startLive() }
                    )
                    AppMode.LIVE -> LiveModeOverlay(
                        bufferStats = bufferStats,
                        exportState = exportState,
                        onSaveMoment = { viewModel.saveMoment() },
                        onStopLive = { viewModel.stopLive() }
                    )
                }
                
                // Debug overlay
                DebugOverlay(
                    currentMode = currentMode,
                    permissionState = permissionState,
                    bufferStats = bufferStats,
                    exportState = exportState
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    viewModel: AppStateManager,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            viewModel.startCamera(lifecycleOwner, previewView)
        }
    )
}

@Composable
fun PermissionScreen(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = if (shouldShowRationale) {
                    "Instant Replay needs camera access to capture and buffer video. Please grant permission to continue."
                } else {
                    "To capture video moments, please allow camera access."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun PreviewModeOverlay(
    onStartLive: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top indicator
        ModeIndicator(
            text = "PREVIEW",
            color = MaterialTheme.colorScheme.secondary,
            isAnimated = false,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .statusBarsPadding()
        )
        
        // Start Live button
        Button(
            onClick = onStartLive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .navigationBarsPadding(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "Start Live",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LiveModeOverlay(
    bufferStats: BufferStats,
    exportState: ExportState,
    onSaveMoment: () -> Unit,
    onStopLive: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeIndicator(
                text = "LIVE",
                color = MaterialTheme.colorScheme.primary,
                isAnimated = true
            )
            
            BufferIndicator(stats = bufferStats)
        }
        
        // Action buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Save Moment button
            Button(
                onClick = onSaveMoment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = exportState !is ExportState.Exporting
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    if (exportState is ExportState.Exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                    }
                    Text(
                        text = if (exportState is ExportState.Exporting) "Saving..." else "Save Moment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Stop Live button
            OutlinedButton(
                onClick = onStopLive,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Stop Live",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Export status toast
        AnimatedVisibility(
            visible = exportState is ExportState.Success,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Saved to Gallery!",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ModeIndicator(
    text: String,
    color: Color,
    isAnimated: Boolean,
    modifier: Modifier = Modifier
) {
    var alpha by remember { mutableFloatStateOf(1f) }
    
    LaunchedEffect(isAnimated) {
        if (isAnimated) {
            while (true) {
                alpha = 0.3f
                kotlinx.coroutines.delay(400)
                alpha = 1f
                kotlinx.coroutines.delay(400)
            }
        }
    }
    
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BufferIndicator(stats: BufferStats) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = String.format("%.1fs", stats.durationSeconds),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "/ 15s",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DebugOverlay(
    currentMode: AppMode,
    permissionState: CameraPermissionState,
    bufferStats: BufferStats,
    exportState: ExportState
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        IconButton(
            onClick = { isExpanded = !isExpanded }
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Info,
                contentDescription = "Debug info",
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DebugRow("Mode", currentMode.name)
                    DebugRow("Camera", permissionState.name)
                    DebugRow("Samples", bufferStats.sampleCount.toString())
                    DebugRow("Duration", String.format("%.2fs", bufferStats.durationSeconds))
                    DebugRow("Export", when (exportState) {
                        is ExportState.Idle -> "Idle"
                        is ExportState.Exporting -> "Exporting..."
                        is ExportState.Success -> "Success"
                        is ExportState.Error -> "Error"
                    })
                }
            }
        }
    }
}

@Composable
fun DebugRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
