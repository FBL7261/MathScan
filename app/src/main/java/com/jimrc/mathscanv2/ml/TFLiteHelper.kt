package com.jimrc.mathscanv2.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(context: Context) {
    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile(context, "ml_models/mnist.tflite")
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun recognizeDigit(bitmap: Bitmap): Int {
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(10) }
        interpreter.run(inputBuffer, output)
        return output[0].indices.maxByOrNull { output[0][it] } ?: -1
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val resizedBitmap = Bitmap.createScaledBitmap(mutableBitmap, 28, 28, false)
        val inputBuffer = ByteBuffer.allocateDirect(28 * 28 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = resizedBitmap.getPixel(x, y)
                val grayValue = ((pixel shr 16 and 0xFF) * 0.299f +
                        (pixel shr 8 and 0xFF) * 0.587f +
                        (pixel and 0xFF) * 0.114f) / 255.0f
                inputBuffer.putFloat(grayValue)
            }
        }
        return inputBuffer
    }
}