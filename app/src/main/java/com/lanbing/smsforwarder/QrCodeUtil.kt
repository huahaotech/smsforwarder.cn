package com.lanbing.smsforwarder

import android.graphics.Bitmap
import android.graphics.Color
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

object QrCodeUtil {

    private val reader by lazy {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }

    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        try {
            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }

            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    fun decodeFromYuv(data: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}