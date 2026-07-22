package com.lanbing.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        previewView = PreviewView(this)

        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.scan_camera_permission), Toast.LENGTH_LONG).show()
                finish()
            }
        }

        setContent {
            val colorScheme = lightColorScheme()

            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography()
            ) {
                val activity = LocalContext.current as Activity
                SideEffect {
                    try {
                        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                        activity.window.statusBarColor = AndroidColor.TRANSPARENT
                        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        controller.isAppearanceLightStatusBars = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            controller.isAppearanceLightNavigationBars = true
                        }
                    } catch (_: Throwable) {}
                }

                ScanScreen(
                    previewView = previewView,
                    onBackClick = { finish() },
                    onRequestCameraPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ZxingQrAnalyzer { barcode ->
                        if (parseAndImportConfig(barcode)) {
                            finish()
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseAndImportConfig(jsonStr: String): Boolean {
        return try {
            JSONObject(jsonStr)
            importConfigFromJson(this, jsonStr) {
                Toast.makeText(this, getString(R.string.scan_success), Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScanActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    previewView: PreviewView,
    onBackClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestCameraPermission: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LocalContext.current.getString(R.string.scan_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalContext.current.getString(R.string.scan_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                AndroidView(
                    factory = {
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .border(
                                width = 3.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val cornerShapes = listOf(
                            Alignment.TopStart to androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp),
                            Alignment.TopEnd to androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 16.dp),
                            Alignment.BottomStart to androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp),
                            Alignment.BottomEnd to androidx.compose.foundation.shape.RoundedCornerShape(bottomEnd = 16.dp)
                        )
                        cornerShapes.forEach { (alignment, shape) ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(
                                        width = 4.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = shape
                                    )
                                    .align(alignment)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = LocalContext.current.getString(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = LocalContext.current.getString(R.string.scan_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

class ZxingQrAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }
    private var isScanned = false

    override fun analyze(imageProxy: ImageProxy) {
        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                // 将 YUV 图像转换为 Bitmap 所需的格式
                val width = mediaImage.width
                val height = mediaImage.height
                val yuvBytes = ByteArray(mediaImage.planes[0].buffer.capacity() +
                    mediaImage.planes[1].buffer.capacity() +
                    mediaImage.planes[2].buffer.capacity())
                
                mediaImage.planes[0].buffer.get(yuvBytes, 0, mediaImage.planes[0].buffer.capacity())
                mediaImage.planes[1].buffer.get(yuvBytes, mediaImage.planes[0].buffer.capacity(), mediaImage.planes[1].buffer.capacity())
                mediaImage.planes[2].buffer.get(yuvBytes, mediaImage.planes[0].buffer.capacity() + mediaImage.planes[1].buffer.capacity(), mediaImage.planes[2].buffer.capacity())

                // 使用 PlanarYUVLuminanceSource 直接处理 YUV 数据
                val source = PlanarYUVLuminanceSource(
                    yuvBytes,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
                )

                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(binaryBitmap)
                
                if (result.text != null) {
                    isScanned = true
                    onBarcodeDetected(result.text)
                }
            } catch (e: Exception) {
                // 解码失败是正常的，继续处理下一帧
            } finally {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}
