package com.whitemason.okaynow.app.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** [text]를 담은 QR. 흰 바탕·검은 점으로 그린다(다크 테마에서도 폰 카메라가 읽도록). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, description: String = "촬영 안내 QR") {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"))
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }.asImageBitmap()
    }
    Image(bitmap, contentDescription = description, modifier = modifier, filterQuality = FilterQuality.None)
}
