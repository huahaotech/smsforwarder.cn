package com.lanbing.smsforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import org.json.JSONObject

class ScanActivity : ComponentActivity() {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isScanned = false
    private var previewView: TextureView? = null
    private var cameraSetupDone = false
    private var cameraPermissionGranted = false

    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startBackgroundThread()
        cameraPermissionGranted = checkCameraPermission()

        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraPermissionGranted = granted
            if (granted) {
                previewView?.let { setupCamera(it) }
            } else {
                Toast.makeText(this, "请授予相机权限以扫描二维码", Toast.LENGTH_LONG).show()
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
                    onBackClick = { finish() },
                    onRequestCameraPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                ) { textureView ->
                    previewView = textureView
                    if (cameraPermissionGranted) {
                        setupCamera(textureView)
                    }
                }
            }
        }

        if (!cameraPermissionGranted) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    private fun setupCamera(textureView: TextureView) {
        if (cameraSetupDone) return
        cameraSetupDone = true

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = getBackCameraId(cameraManager) ?: run {
                Toast.makeText(this, "无法找到后置相机", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val previewSize = chooseOptimalSize(cameraManager, cameraId, 1920, 1080)

            imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, android.graphics.ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    processImage(image)
                    image.close()
                }
            }, backgroundHandler)

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera(cameraManager, cameraId, surface, previewSize)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }

            if (textureView.isAvailable) {
                openCamera(cameraManager, cameraId, textureView.surfaceTexture, previewSize)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera setup failed", e)
            cameraSetupDone = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraManager: CameraManager, cameraId: String, surfaceTexture: SurfaceTexture, previewSize: Size) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession(camera, surfaceTexture, previewSize)
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }
            override fun onError(camera: CameraDevice, errorCode: Int) {
                camera.close()
                cameraDevice = null
                Log.e(TAG, "Camera error: $errorCode")
            }
        }, backgroundHandler)
    }

    private fun createCaptureSession(camera: CameraDevice, surfaceTexture: SurfaceTexture, previewSize: Size) {
        val previewSurface = Surface(surfaceTexture)
        val readerSurface = imageReader?.surface ?: return

        try {
            camera.createCaptureSession(
                listOf(previewSurface, readerSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            builder.addTarget(previewSurface)
                            builder.addTarget(readerSurface)
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_AUTO)
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "Capture session error", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create capture session failed", e)
        }
    }

    private fun processImage(image: Image) {
        if (isScanned) {
            image.close()
            return
        }

        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            yBuffer.rewind()

            val yData = ByteArray(width * height)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(yData, row * width, width)
            }

            val result = QrCodeUtil.decodeFromYuv(yData, width, height)
            if (result != null) {
                isScanned = true
                runOnUiThread {
                    if (parseAndImportConfig(result)) {
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image processing error", e)
        }
    }

    private fun parseAndImportConfig(jsonStr: String): Boolean {
        return try {
            JSONObject(jsonStr)
            importConfigFromJson(this, jsonStr) {
                Toast.makeText(this, "配置导入成功", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getBackCameraId(cameraManager: CameraManager): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun chooseOptimalSize(cameraManager: CameraManager, cameraId: String, targetWidth: Int, targetHeight: Int): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val choices = configs.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)

        var optimalSize = choices[0]
        var minDiff = java.lang.Math.abs(optimalSize.width - targetWidth) + java.lang.Math.abs(optimalSize.height - targetHeight)

        for (size in choices) {
            val diff = java.lang.Math.abs(size.width - targetWidth) + java.lang.Math.abs(size.height - targetHeight)
            if (diff < minDiff) {
                optimalSize = size
                minDiff = diff
            }
        }
        return optimalSize
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanned = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
    }

    companion object {
        private const val TAG = "ScanActivity"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBackClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestCameraPermission: () -> Unit,
    onTextureViewReady: (TextureView) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "扫描二维码",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    factory = { context ->
                        TextureView(context).apply {
                            onTextureViewReady(this)
                        }
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
                        text = "将二维码对准扫描框",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "系统将自动识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}