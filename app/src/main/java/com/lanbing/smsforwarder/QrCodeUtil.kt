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
    private const val MAX_QR_DATA_SIZE = 600
    private const val COMPRESSION_THRESHOLD = 200
    private const val CHUNK_PREFIX = "P"
    private const val PART_HEADER_PATTERN = Regex("""^P(\d+)/(\d+):(.*)""")

    private val reader by lazy {
        MultiFormatReader().apply {
            setHints(mutableMapOf<DecodeHintType, Any>().apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            })
        }
    }

    fun generateQrCodes(content: String, size: Int = 1024): List<Bitmap> {
        val data = encodeContent(content)
        if (data.length <= MAX_QR_DATA_SIZE) {
            val bitmap = generateSingleQr(data, size)
            return if (bitmap != null) listOf(bitmap) else emptyList()
        }
        return splitAndGenerate(data, size)
    }

    private fun splitAndGenerate(data: String, size: Int): List<Bitmap> {
        val parts = mutableListOf<String>()
        var remaining = data
        var index = 0
        while (remaining.isNotEmpty()) {
            index++
            val chunkSize = MAX_QR_DATA_SIZE
            val chunk = remaining.take(chunkSize)
            remaining = remaining.drop(chunkSize)
            val partHeader = "${CHUNK_PREFIX}$index/${0}:"
            parts.add("$partHeader$chunk")
        }
        val total = parts.size
        val fixedParts = parts.map { part ->
            val match = PART_HEADER_PATTERN.find(part)
            if (match != null) {
                val idx = match.groupValues[1]
                val body = match.groupValues[3]
                "${CHUNK_PREFIX}$idx/$total:$body"
            } else part
        }
        return fixedParts.mapNotNull { generateSingleQr(it, size) }
    }

    private fun generateSingleQr(data: String, size: Int): Bitmap? {
        return try {
            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
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
        val base64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
        return "C1:$base64"
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

    data class DecodeResult(
        val text: String?,
        val isPart: Boolean,
        val partIndex: Int,
        val partTotal: Int
    )

    fun decodeFromBitmap(bitmap: Bitmap): DecodeResult {
        val strategies = listOf(
            { decodeBitmapDirect(bitmap) },
            { decodeBitmapCenterCrop(bitmap) },
            { decodeBitmapInverted(bitmap) }
        )
        for (strategy in strategies) {
            val result = strategy()
            if (result != null) return result
        }
        return DecodeResult(null, false, 0, 0)
    }

    private fun decodeBitmapDirect(bitmap: Bitmap): DecodeResult? {
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            parseDecodedText(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBitmapCenterCrop(bitmap: Bitmap): DecodeResult? {
        return try {
            val cropW = (bitmap.width * 0.6).toInt()
            val cropH = (bitmap.height * 0.6).toInt()
            val offsetX = (bitmap.width - cropW) / 2
            val offsetY = (bitmap.height - cropH) / 2
            val cropped = Bitmap.createBitmap(bitmap, offsetX, offsetY, cropW, cropH)
            try {
                val pixels = IntArray(cropped.width * cropped.height)
                cropped.getPixels(pixels, 0, cropped.width, 0, 0, cropped.width, cropped.height)
                val source = RGBLuminanceSource(cropped.width, cropped.height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(binaryBitmap)
                parseDecodedText(result.text)
            } finally {
                cropped.recycle()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBitmapInverted(bitmap: Bitmap): DecodeResult? {
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val inverted = IntArray(pixels.size)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = 255 - Color.red(color)
                val g = 255 - Color.green(color)
                val b = 255 - Color.blue(color)
                inverted[i] = Color.rgb(r, g, b)
            }
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, inverted)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            parseDecodedText(result.text)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeFromYuv(data: ByteArray, width: Int, height: Int): DecodeResult {
        val strategies = listOf(
            { decodeYuvFull(data, width, height) },
            { decodeYuvCenterCrop(data, width, height) },
            { decodeYuvDownsampled(data, width, height) }
        )
        for (strategy in strategies) {
            val result = strategy()
            if (result != null) return result
        }
        return DecodeResult(null, false, 0, 0)
    }

    private fun decodeYuvFull(data: ByteArray, width: Int, height: Int): DecodeResult? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            parseDecodedText(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeYuvCenterCrop(data: ByteArray, width: Int, height: Int): DecodeResult? {
        return try {
            val cropW = (width * 0.6).toInt()
            val cropH = (height * 0.6).toInt()
            val offsetX = (width - cropW) / 2
            val offsetY = (height - cropH) / 2
            val source = PlanarYUVLuminanceSource(data, width, height, offsetX, offsetY, cropW, cropH, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            parseDecodedText(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeYuvDownsampled(data: ByteArray, width: Int, height: Int): DecodeResult? {
        return try {
            val scale = 2
            val newWidth = width / scale
            val newHeight = height / scale
            val newData = ByteArray(newWidth * newHeight)
            for (y in 0 until newHeight) {
                for (x in 0 until newWidth) {
                    var sum = 0
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            sum += data[(y * scale + dy) * width + (x * scale + dx)].toInt() and 0xFF
                        }
                    }
                    newData[y * newWidth + x] = (sum / (scale * scale)).toByte()
                }
            }
            val source = PlanarYUVLuminanceSource(newData, newWidth, newHeight, 0, 0, newWidth, newHeight, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            parseDecodedText(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDecodedText(raw: String): DecodeResult {
        val partMatch = PART_HEADER_PATTERN.find(raw)
        if (partMatch != null) {
            val index = partMatch.groupValues[1].toInt()
            val total = partMatch.groupValues[2].toInt()
            val body = partMatch.groupValues[3]
            return DecodeResult(body, isPart = true, partIndex = index, partTotal = total)
        }
        val decoded = decodeContent(raw)
        return DecodeResult(decoded, isPart = false, partIndex = 0, partTotal = 0)
    }

    fun assembleParts(parts: Map<Int, String>, total: Int): String? {
        if (parts.size < total) return null
        val ordered = (1..total).mapNotNull { parts[it] }
        if (ordered.size != total) return null
        val combined = ordered.joinToString("")
        return decodeContent(combined)
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