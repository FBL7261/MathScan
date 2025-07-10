package com.jimrc.mathscanv2.math

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

object OperatorRecognizer {
    fun recognize(bitmap: Bitmap): String {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val aspectRatio = mat.width().toDouble() / mat.height().toDouble()

        if (aspectRatio > 2.0) return "-" // Signo de resta
        if (aspectRatio < 0.4) return "/" // Signo de división (muy alto)

        // El signo de suma es más difícil, a menudo se confunde con dígitos
        val whitePixels = Core.countNonZero(mat)
        val totalPixels = mat.width() * mat.height()
        val fillRatio = whitePixels.toDouble() / totalPixels
        if(fillRatio < 0.35) return "+"

        return "*" // Asumir multiplicación para otros casos
    }
}