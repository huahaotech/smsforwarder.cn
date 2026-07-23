package com.lanbing.smsforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
    private lateinit var imagePickerLauncher: ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>

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

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                try {
                    val content = contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                        bitmap?.let { QrCodeUtil.decodeFromBitmap(it) }
                    }
                    if (content != null) {
                        returnScanResult(content)
                    } else {
                        Toast.makeText(this, "未能识别图片中的二维码", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show()
                }
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
                    onRequestCameraPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickFromGallery = {
                        imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
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

    private fun returnScanResult(jsonStr: String) {
        val intent = Intent().apply {
            putExtra("SCAN_RESULT", jsonStr)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
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
                openCamera(cameraManager, cameraId, textureView.surfaceTexture!!, previewSize)
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
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
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
                    returnScanResult(result)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image processing error", e)
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
        val choices = configs?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888) ?: arrayOf(Size(1920, 1080))

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
        const val EXTRA_SCAN_RESULT = "SCAN_RESULT"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBackClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestCameraPermission: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTextureViewReady: (TextureView) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "扫描二维码",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Black
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "将二维码对准屏幕，自动识别",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = onPickFromGallery,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从相册选取")
                }
            }
        }
    }
}