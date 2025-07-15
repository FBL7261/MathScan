package com.jimrc.mathscanv2

import android.util.Log
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MongoService {
    private const val APP_ID = "app-bxnzzsu"
    private val app = App.create(AppConfiguration.Builder(APP_ID).build())
    private var realm: Realm? = null

    suspend fun connectAnonymously(): Boolean {
        return try {
            val user = app.login(Credentials.anonymous())

            val config = SyncConfiguration.Builder(user, setOf(Estudiante::class))
                .initialSubscriptions { realm: Realm ->
                    if (findByName("estudiantes-todos") == null) {
                        add(
                            realm.query<Estudiante>(),
                            "estudiantes-todos"
                        )
                    }
                }
                .build()

            realm = Realm.open(config)
            true
        } catch (e: Exception) {
            Log.e("MongoService", "Error al conectar: ${e.message}")
            false
        }
    }

    suspend fun estudianteExiste(nombre: String): Boolean {
        val normalizado = nombre.trim().lowercase()
        val estudiantes = realm?.query<Estudiante>("nombre == $0", normalizado)?.find()
        return !estudiantes.isNullOrEmpty()
    }

    suspend fun agregarEstudiante(nombre: String, curso: String) {
        val normalizado = nombre.trim().lowercase()
        realm?.write {
            copyToRealm(
                Estudiante().apply {
                    this.nombre = normalizado
                    this.curso = curso
                }
            )
        }
    }
}
