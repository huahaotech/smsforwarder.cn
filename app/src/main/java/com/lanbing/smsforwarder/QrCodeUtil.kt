package com.lanbing.smsforwarder

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object QrCodeUtil {

    private const val TAG = "QrCodeUtil"
    private const val MAX_QR_DATA_SIZE = 1200
    private const val COMPRESSION_THRESHOLD = 200

    private val reader by lazy {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }

    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val data = encodeContent(content)
            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }

            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "生成二维码失败", e)
            null
        }
    }

    private fun encodeContent(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size <= COMPRESSION_THRESHOLD) {
            return content
        }

        val compressed = deflateCompress(bytes)
        if (compressed.size >= bytes.size) {
            return content
        }

        if (compressed.size <= MAX_QR_DATA_SIZE) {
            val base64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
            return "C1:$base64"
        }

        val compressed2 = deflateCompress(content.toByteArray(Charsets.UTF_8), level = Deflater.BEST_COMPRESSION)
        val base64 = android.util.Base64.encodeToString(compressed2, android.util.Base64.NO_WRAP)
        if (base64.length <= MAX_QR_DATA_SIZE) {
            return "C2:$base64"
        }

        throw Exception("配置数据过大（${content.length}字符），无法生成二维码")
    }

    private fun deflateCompress(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
        val deflater = Deflater(level, true)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(data.size * 2)
        val output = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            decodeContent(result.text)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeFromYuv(data: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            decodeContent(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeContent(encoded: String): String {
        return when {
            encoded.startsWith("C1:") -> {
                val base64 = encoded.removePrefix("C1:")
                val compressed = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val decompressed = inflateDecompress(compressed)
                String(decompressed, Charsets.UTF_8)
            }
            encoded.startsWith("C2:") -> {
                val base64 = encoded.removePrefix("C2:")
                val compressed = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val decompressed = inflateDecompress(compressed)
                String(decompressed, Charsets.UTF_8)
            }
            else -> encoded
        }
    }

    private fun inflateDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(data)
        val buffer = ByteArray(data.size * 4)
        val output = ByteArrayOutputStream()
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            output.write(buffer, 0, count)
        }
        inflater.end()
        return output.toByteArray()
    }
}