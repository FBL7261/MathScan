package com.jimrc.mathscanv2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jimrc.mathscanv2.databinding.ActivityStartBinding
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast


class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding
    private lateinit var imageAdapter: ImageAdapter
    private val proofImages = mutableListOf<File>()
    private val sharedPref by lazy {
        getSharedPreferences("math_scan_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)



        // Configurar el Spinner con los cursos
        val courses = arrayOf("1ro Básico", "2do Básico", "3ro Básico", "4to Básico")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.courseSpinner.adapter = adapter

        // Configurar el botón para empezar
        binding.startButton.setOnClickListener {
            val studentNameRaw = binding.studentNameEditText.text.toString().trim()
            val selectedCourse = binding.courseSpinner.selectedItem.toString()

            if (studentNameRaw.isEmpty()) {
                binding.studentNameEditText.error = "El nombre es requerido"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val conectado = MongoService.connectAnonymously()
                if (conectado) {
                    val yaExiste = MongoService.estudianteExiste(studentNameRaw)
                    if (!yaExiste) {
                        MongoService.agregarEstudiante(studentNameRaw, selectedCourse)
                        Toast.makeText(this@StartActivity, "Nuevo estudiante creado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@StartActivity, "Estudiante encontrado", Toast.LENGTH_SHORT).show()
                    }

                    // Ir a la siguiente pantalla
                    val intent = Intent(this@StartActivity, MainActivity::class.java).apply {
                        putExtra("STUDENT_NAME", studentNameRaw)
                        putExtra("COURSE_NAME", selectedCourse)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@StartActivity, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
                }
            }
        }


        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        // Cargar puntaje e historial cada vez que la actividad se vuelve visible
        loadScore()
        loadProofImages()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(proofImages)
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@StartActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imageAdapter
        }
    }

    private fun loadScore() {
        val correctAnswers = sharedPref.getInt("correctAnswers", 0)
        val totalAnswers = sharedPref.getInt("totalAnswers", 0)
        binding.scoreTextView.text = "Correctas: $correctAnswers / $totalAnswers"
    }

    private fun loadProofImages() {
        proofImages.clear()
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        storageDir?.listFiles { file -> file.isFile && file.name.startsWith("MATHSCAN_") }
            ?.sortedByDescending { it.lastModified() } // Ordenar por más reciente
            ?.let {
                proofImages.addAll(it)
            }
        imageAdapter.notifyDataSetChanged() // Notificar al adaptador que los datos cambiaron
    }
}

// --- Adaptador para el RecyclerView ---
class ImageAdapter(private val imageFiles: List<File>) :
    RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.proofImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proof_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageFile = imageFiles[position]
        Glide.with(holder.itemView.context)
            .load(imageFile)
            .centerCrop()
            .into(holder.imageView)
    }

    override fun getItemCount() = imageFiles.size
}