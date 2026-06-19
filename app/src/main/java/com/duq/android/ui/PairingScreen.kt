package com.duq.android.ui

import android.Manifest
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPaired: () -> Unit
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    var hasCamera by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(status) {
        if (status is PairingViewModel.PairingStatus.Success) onPaired()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Pair with DUQ") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "1. Send /pair to DUQ in Telegram\n2. Scan the QR code",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            when (val s = status) {
                is PairingViewModel.PairingStatus.Idle -> {
                    if (hasCamera && !showManual) {
                        QrScanner(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            onQrDetected = { raw ->
                                if (!scanned) {
                                    scanned = true
                                    parseSetupCode(raw)?.let { (url, token) ->
                                        viewModel.pair(url, token)
                                    } ?: run { scanned = false }
                                }
                            }
                        )
                    }
                    TextButton(onClick = { showManual = !showManual }) {
                        Text(if (showManual) "Use camera" else "Enter code manually")
                    }
                    if (showManual) {
                        OutlinedTextField(
                            value = manualCode, onValueChange = { manualCode = it },
                            label = { Text("Setup code (base64)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Button(onClick = {
                            parseSetupCode(manualCode)?.let { (url, token) -> viewModel.pair(url, token) }
                        }, enabled = manualCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("Pair")
                        }
                    }
                }
                is PairingViewModel.PairingStatus.Waiting -> {
                    CircularProgressIndicator()
                    Text("Waiting for approval in Telegram...\nRun: openclaw devices approve",
                        textAlign = TextAlign.Center)
                }
                is PairingViewModel.PairingStatus.Error -> {
                    Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.reset(); scanned = false }) { Text("Retry") }
                }
                is PairingViewModel.PairingStatus.Success -> {
                    Text("Paired!", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QrScanner(modifier: Modifier = Modifier, onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }

    AndroidView(modifier = modifier, factory = { ctx ->
        PreviewView(ctx).apply {
            val provider = ProcessCameraProvider.getInstance(ctx)
            provider.addListener({
                val cp = provider.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    proxy.image?.let { img ->
                        val image = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { onQrDetected(it) }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } ?: proxy.close()
                }
                runCatching {
                    cp.unbindAll()
                    cp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
    })
}

private fun parseSetupCode(raw: String): Pair<String, String>? {
    return try {
        // Try direct JSON first, then base64
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject(String(Base64.decode(raw, Base64.DEFAULT)))
        }
        val url = json.getString("url")
        val token = json.getString("bootstrapToken")
        if (url.isNotBlank() && token.isNotBlank()) url to token else null
    } catch (e: Exception) {
        Log.w("PairingScreen", "Failed to parse setup code: ${e.message}")
        null
    }
}
