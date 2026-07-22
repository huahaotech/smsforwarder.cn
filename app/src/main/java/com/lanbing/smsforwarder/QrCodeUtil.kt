package com.lanbing.smsforwarder

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtil {
    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        try {
            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }

            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            // 使用批量设置方式，比逐个setPixel()快很多
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }
    }
}
