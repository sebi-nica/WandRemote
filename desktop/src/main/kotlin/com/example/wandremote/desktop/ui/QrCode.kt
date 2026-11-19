package com.example.wandremote.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Draws [text] as a QR code. Always black on white (regardless of theme) so phone cameras can read it.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encode(text) }
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        val cell = minOf(size.width, size.height) / matrix.width
        val origin = Offset((size.width - cell * matrix.width) / 2, (size.height - cell * matrix.height) / 2)
        // Slightly oversized cells avoid hairline gaps between neighbouring modules.
        val cellSize = Size(cell + 0.5f, cell + 0.5f)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(origin.x + x * cell, origin.y + y * cell), cellSize)
                }
            }
        }
    }
}

private fun encode(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0, // the white padding around the canvas is the quiet zone
    ),
)
