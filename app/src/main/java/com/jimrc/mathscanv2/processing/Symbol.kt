package com.jimrc.mathscanv2.processing

import android.graphics.Bitmap
import org.opencv.core.Rect

data class Symbol(
    val bitmap: Bitmap,
    val boundingBox: Rect,
    val type: SymbolType
)

enum class SymbolType {
    DIGIT,
    OPERATOR,
    UNKNOWN
}