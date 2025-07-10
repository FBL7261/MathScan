package com.jimrc.mathscanv2.processing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object ImageProcessor {

    /**
     * Busca todos los contornos de los símbolos en la imagen y los agrupa en un único
     * rectángulo que encierra toda la expresión matemática.
     *
     * @param imageMat La imagen de entrada en formato Mat (se espera RGBA de la cámara).
     * @return El Rect que encierra toda la expresión, o null si no se encuentra nada.
     */
    fun findExerciseContour(imageMat: Mat): Rect? {
        val grayMat = Mat()
        Imgproc.cvtColor(imageMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        // El umbral binario inverso es excelente para encontrar texto oscuro sobre un fondo claro.
        val binaryMat = Mat()
        Imgproc.threshold(grayMat, binaryMat, 125.0, 255.0, Imgproc.THRESH_BINARY_INV)

        // Encontrar todos los contornos de los posibles símbolos.
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Filtrar los contornos para quedarse solo con los que parecen símbolos por su tamaño.
        val symbolRects = contours
            .map { Imgproc.boundingRect(it) }
            .filter { it.area() > 100 && it.area() < 20000 } // Filtra ruido y áreas demasiado grandes

        // Si no se encontraron símbolos, no hay nada que encerrar.
        if (symbolRects.isEmpty()) {
            return null
        }

        // Combinar todos los rectángulos de los símbolos en uno solo.
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = 0
        var maxY = 0

        for (rect in symbolRects) {
            minX = minOf(minX, rect.x)
            minY = minOf(minY, rect.y)
            maxX = maxOf(maxX, rect.x + rect.width)
            maxY = maxOf(maxY, rect.y + rect.height)
        }

        // Añadir un pequeño margen (padding) para que el contorno no esté pegado a los números.
        val padding = 20
        val finalX = (minX - padding).coerceAtLeast(0)
        val finalY = (minY - padding).coerceAtLeast(0)
        val finalWidth = (maxX - minX + padding * 2).coerceAtMost(imageMat.width() - finalX)
        val finalHeight = (maxY - minY + padding * 2).coerceAtMost(imageMat.height() - finalY)

        return Rect(finalX, finalY, finalWidth, finalHeight)
    }

    /**
     * Esta función se usa DESPUÉS de recortar la imagen. Extrae los símbolos
     * del bitmap ya recortado para su reconocimiento.
     */
    fun extractSymbols(bitmap: Bitmap): List<Symbol> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        // Si el bitmap ya viene en escala de grises, no es necesario convertir.
        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        }
        Imgproc.threshold(mat, mat, 120.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        return contours
            .filter { Imgproc.contourArea(it) > 50.0 }
            .map {
                val rect = Imgproc.boundingRect(it)
                val symbolMat = Mat(mat, rect)
                val symbolBitmap = Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(symbolMat, symbolBitmap)

                // Heurística simple para decidir si es un dígito u operador
                val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                val type = if (aspectRatio > 1.8 || aspectRatio < 0.4) {
                    SymbolType.OPERATOR
                } else {
                    SymbolType.DIGIT
                }
                Symbol(symbolBitmap, rect, type)
            }
            .sortedBy { it.boundingBox.x }
    }
}