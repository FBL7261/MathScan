package com.jimrc.mathscanv2.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Rect

/**
 * Una vista personalizada que se superpone a la vista previa de la cámara
 * para dibujar un contorno alrededor del ejercicio detectado.
 */
class ContourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val contourPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var contourRect: RectF? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    /**
     * Establece el rectángulo del contorno a dibujar.
     * Las coordenadas deben provenir de la imagen de análisis.
     * @param rect El Rect de OpenCV que se va a dibujar, o null para borrar.
     * @param sourceWidth El ancho de la imagen en la que se detectó el contorno.
     * @param sourceHeight El alto de la imagen en la que se detectó el contorno.
     */
    fun setContour(rect: Rect?, sourceWidth: Int, sourceHeight: Int) {
        if (rect != null) {
            // Es crucial rotar las dimensiones si la imagen de análisis está rotada
            // en comparación con la vista. CameraX a menudo rota la imagen de análisis
            // 90 grados en modo retrato.
            val scaleX = width.toFloat() / sourceHeight.toFloat()
            val scaleY = height.toFloat() / sourceWidth.toFloat()

            // Mapea las coordenadas del rectángulo detectado al sistema de coordenadas de la vista.
            // Nota: x e y se intercambian porque la imagen de origen está rotada.
            val left = rect.y * scaleX
            val top = rect.x * scaleY
            val right = (rect.y + rect.height) * scaleX
            val bottom = (rect.x + rect.width) * scaleY

            this.contourRect = RectF(left, top, right, bottom)
        } else {
            this.contourRect = null
        }
        // Vuelve a dibujar la vista para mostrar/ocultar el nuevo contorno.
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Limpia cualquier dibujo anterior
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), clearPaint)

        // Dibuja el nuevo rectángulo si existe
        contourRect?.let {
            canvas.drawRect(it, contourPaint)
        }
    }
}