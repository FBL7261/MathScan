package com.jimrc.mathscanv2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jimrc.mathscanv2.camera.CameraManager
import com.jimrc.mathscanv2.databinding.ActivityMainBinding
import com.jimrc.mathscanv2.math.OperatorRecognizer
import com.jimrc.mathscanv2.ml.TFLiteHelper
import com.jimrc.mathscanv2.processing.ImageProcessor
import com.jimrc.mathscanv2.processing.SymbolType
import net.objecthunter.exp4j.ExpressionBuilder
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var tfLiteHelper: TFLiteHelper
    private lateinit var imageAnalysisExecutor: ExecutorService

    private var studentName: String = ""
    private val sharedPref by lazy {
        getSharedPreferences("math_scan_prefs", Context.MODE_PRIVATE)
    }

    // Variables para guardar el último contorno detectado y las dimensiones de la imagen de análisis
    private var latestContour: org.opencv.core.Rect? = null
    private var analysisImageWidth: Int = 0
    private var analysisImageHeight: Int = 0


    // --- INICIO DE LA LÓGICA DE ZOOM ---
    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoom() {
        val scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cameraInfo = cameraManager.getCameraInfo() ?: return true
                    val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    val newZoomRatio = (currentZoomRatio * delta).coerceIn(
                        cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                        cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    )
                    cameraManager.setZoom(newZoomRatio)
                    return true
                }
            })

        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }
    // --- FIN DE LA LÓGICA DE ZOOM ---


    object MathValidator {
        enum class Status { CORRECT, INCORRECT, INVALID_FORMAT }
        data class ValidationResult(val status: Status, val correctAnswer: Double? = null)

        fun validateEquation(fullEquation: String): ValidationResult {
            if (!fullEquation.contains("=") || !fullEquation.any { it.isDigit() }) {
                return ValidationResult(Status.INVALID_FORMAT)
            }
            val parts = fullEquation.split("=")
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return ValidationResult(Status.INVALID_FORMAT)
            }
            val (equation, userAnswerStr) = parts
            val userAnswer = userAnswerStr.toDoubleOrNull()
            return try {
                val correctAnswer = ExpressionBuilder(equation).build().evaluate()
                if (userAnswer != null && correctAnswer == userAnswer) {
                    ValidationResult(Status.CORRECT, correctAnswer)
                } else {
                    ValidationResult(Status.INCORRECT, correctAnswer)
                }
            } catch (e: Exception) {
                ValidationResult(Status.INVALID_FORMAT)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    // Para imágenes de galería, procesamos la imagen completa
                    imageAnalysisExecutor.execute { processImageAndValidate(bitmap) }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageAnalysisExecutor = Executors.newSingleThreadExecutor()
        studentName = intent.getStringExtra("STUDENT_NAME") ?: "N/A"

        if (OpenCVLoader.initLocal()) {
            Log.i("OpenCV", "OpenCV se cargó correctamente")
        } else {
            Toast.makeText(this, "Falló la inicialización de OpenCV", Toast.LENGTH_LONG).show()
            return
        }

        tfLiteHelper = TFLiteHelper(this)

        if (allPermissionsGranted()) {
            cameraManager = CameraManager(this, this, binding.previewView) { contour, width, height ->
                // Actualiza el último contorno detectado y las dimensiones de la imagen
                this.latestContour = contour
                this.analysisImageWidth = width
                this.analysisImageHeight = height
                // Dibuja el contorno en la superposición
                binding.contourOverlayView.setContour(contour, width, height)
            }
            cameraManager.startCamera()
            setupZoom()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        // --- LÓGICA DEL BOTÓN DE VALIDAR (MODIFICADA) ---
        binding.captureButton.setOnClickListener {
            binding.captureButton.isEnabled = false
            val currentContour = latestContour

            // Si no hay un contorno detectado, no hacer nada
            if (currentContour == null) {
                runOnUiThread {
                    Toast.makeText(this, "Apunte la cámara a un ejercicio.", Toast.LENGTH_SHORT).show()
                    binding.captureButton.isEnabled = true
                }
                return@setOnClickListener
            }

            // Tomar una foto de alta resolución
            cameraManager.takePhoto { fullBitmap ->
                // Recortar el bitmap usando el último contorno detectado
                val croppedBitmap = cropBitmapFromContour(fullBitmap, currentContour)
                // Procesar y validar solo la imagen recortada
                imageAnalysisExecutor.execute { processImageAndValidate(croppedBitmap) }
            }
        }
    }

    /**
     * Recorta un Bitmap usando las coordenadas de un Rect de OpenCV.
     * Considera la rotación entre la imagen de análisis y la foto final.
     */
    private fun cropBitmapFromContour(sourceBitmap: Bitmap, contour: org.opencv.core.Rect): Bitmap {
        // La imagen de análisis está rotada 90 grados. Sus dimensiones 'width' y 'height'
        // corresponden a 'height' y 'width' de la foto final.
        val scaleX = sourceBitmap.width.toFloat() / analysisImageHeight.toFloat()
        val scaleY = sourceBitmap.height.toFloat() / analysisImageWidth.toFloat()

        // Mapear las coordenadas del contorno al sistema de la foto final
        // contour.y -> coord X en la foto; contour.x -> coord Y en la foto
        val cropX = (contour.y * scaleX).toInt()
        val cropY = (contour.x * scaleY).toInt()
        val cropWidth = (contour.height * scaleX).toInt()
        val cropHeight = (contour.width * scaleY).toInt()

        // Asegurar que las coordenadas de recorte estén dentro de los límites del bitmap
        val finalX = cropX.coerceAtLeast(0)
        val finalY = cropY.coerceAtLeast(0)
        val finalWidth = if (finalX + cropWidth > sourceBitmap.width) sourceBitmap.width - finalX else cropWidth
        val finalHeight = if (finalY + cropHeight > sourceBitmap.height) sourceBitmap.height - finalY else cropHeight

        if (finalWidth <= 0 || finalHeight <= 0) {
            return sourceBitmap // Devuelve la imagen original si el recorte no es válido
        }

        return try {
            Bitmap.createBitmap(sourceBitmap, finalX, finalY, finalWidth, finalHeight)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al recortar el bitmap", e)
            sourceBitmap // Fallback a la imagen completa si el recorte falla
        }
    }


    private fun processImageAndValidate(bitmap: Bitmap) {
        val symbols = ImageProcessor.extractSymbols(bitmap)
        val recognizedExpression = StringBuilder()
        for (symbol in symbols) {
            val char = when (symbol.type) {
                SymbolType.DIGIT -> tfLiteHelper.recognizeDigit(symbol.bitmap).toString()
                SymbolType.OPERATOR -> OperatorRecognizer.recognize(symbol.bitmap)
                else -> ""
            }
            recognizedExpression.append(char)
        }
        val expressionText = recognizedExpression.toString()
        Log.i("MainActivity", "Expresión Reconocida: $expressionText")

        val validationResult = MathValidator.validateEquation(expressionText)
        val resultMessage: String

        when (validationResult.status) {
            MathValidator.Status.CORRECT -> {
                resultMessage = "✅ Correcto: $expressionText"
                saveScore(wasCorrect = true)
                saveProofImage(bitmap, "Correcto") // Guarda el bitmap (ya recortado)
            }
            MathValidator.Status.INCORRECT -> {
                val correctAnswer = validationResult.correctAnswer?.toInt()
                resultMessage = "❌ Incorrecto. La respuesta era: ${correctAnswer ?: "Inválido"}"
                saveScore(wasCorrect = false)
                saveProofImage(bitmap, "Incorrecto") // Guarda el bitmap (ya recortado)
            }
            MathValidator.Status.INVALID_FORMAT -> {
                resultMessage = "⚠️ No se detectó un ejercicio válido."
            }
        }
        showResultOnUI(resultMessage)
    }

    private fun saveScore(wasCorrect: Boolean) {
        val currentCorrect = sharedPref.getInt("correctAnswers", 0)
        val currentTotal = sharedPref.getInt("totalAnswers", 0)
        with(sharedPref.edit()) {
            if (wasCorrect) {
                putInt("correctAnswers", currentCorrect + 1)
            }
            putInt("totalAnswers", currentTotal + 1)
            apply()
        }
    }

    private fun saveProofImage(bitmap: Bitmap, result: String) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "MATHSCAN_${studentName.replace(" ", "_")}_${timeStamp}_$result.jpg"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null) {
            val imageFile = File(storageDir, fileName)
            try {
                val fos = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()
                Log.i("MainActivity", "Imagen guardada en: ${imageFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("MainActivity", "Error al guardar imagen de comprobante", e)
            }
        }
    }

    private fun showResultOnUI(message: String) {
        runOnUiThread {
            binding.resultText.text = message
            binding.resultText.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                binding.resultText.visibility = View.INVISIBLE
                binding.captureButton.isEnabled = true
            }, 3000)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraManager = CameraManager(this, this, binding.previewView) { contour, width, height ->
                    this.latestContour = contour
                    this.analysisImageWidth = width
                    this.analysisImageHeight = height
                    binding.contourOverlayView.setContour(contour, width, height)
                }
                cameraManager.startCamera()
                setupZoom()
            } else {
                Toast.makeText(this, "Permisos no concedidos por el usuario.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
        if (::imageAnalysisExecutor.isInitialized) {
            imageAnalysisExecutor.shutdown()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}