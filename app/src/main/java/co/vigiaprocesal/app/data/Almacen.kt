package co.vigiaprocesal.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Guarda en el teléfono la lista de procesos seguidos (procesos.json) y una
 * copia de las actuaciones de cada uno (actuaciones/<idProceso>.json), para
 * poder verlas sin conexión.
 */
class Almacen private constructor(dir: File) {
    private val archivo = File(dir, "procesos.json")
    private val dirActuaciones = File(dir, "actuaciones").apply { mkdirs() }
    private val candado = Mutex()
    private val _estado = MutableStateFlow(leerEstado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    fun proceso(id: Long): ProcesoSeguido? = _estado.value.procesos.firstOrNull { it.idProceso == id }

    suspend fun actualizar(cambio: (Estado) -> Estado) {
        candado.withLock {
            val nuevo = cambio(_estado.value)
            _estado.value = nuevo
            withContext(Dispatchers.IO) {
                escribirAtomico(archivo, Cpnu.json.encodeToString(Estado.serializer(), nuevo))
            }
        }
    }

    suspend fun actualizarProceso(id: Long, cambio: (ProcesoSeguido) -> ProcesoSeguido) {
        actualizar { e -> e.copy(procesos = e.procesos.map { if (it.idProceso == id) cambio(it) else it }) }
    }

    suspend fun guardarActuaciones(id: Long, lista: List<Actuacion>) {
        withContext(Dispatchers.IO) {
            escribirAtomico(
                File(dirActuaciones, "$id.json"),
                Cpnu.json.encodeToString(ListSerializer(Actuacion.serializer()), lista),
            )
        }
    }

    suspend fun leerActuaciones(id: Long): List<Actuacion> = withContext(Dispatchers.IO) {
        val f = File(dirActuaciones, "$id.json")
        if (!f.exists()) {
            emptyList()
        } else {
            runCatching {
                Cpnu.json.decodeFromString(ListSerializer(Actuacion.serializer()), f.readText())
            }.getOrDefault(emptyList())
        }
    }

    fun tieneActuaciones(id: Long): Boolean = File(dirActuaciones, "$id.json").exists()

    suspend fun borrarActuaciones(id: Long) {
        withContext(Dispatchers.IO) { File(dirActuaciones, "$id.json").delete() }
    }

    private fun leerEstado(): Estado {
        if (!archivo.exists()) return Estado()
        return try {
            Cpnu.json.decodeFromString(Estado.serializer(), archivo.readText())
        } catch (e: Exception) {
            // No sobrescribir un archivo dañado: se aparta para poder recuperarlo.
            archivo.renameTo(File(archivo.parentFile, "procesos.danado.${System.currentTimeMillis()}.json"))
            Estado()
        }
    }

    private fun escribirAtomico(destino: File, texto: String) {
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        tmp.writeText(texto)
        if (!tmp.renameTo(destino)) {
            destino.delete()
            tmp.renameTo(destino)
        }
    }

    companion object {
        @Volatile
        private var instancia: Almacen? = null

        fun get(context: Context): Almacen =
            instancia ?: synchronized(this) {
                instancia ?: Almacen(context.applicationContext.filesDir).also { instancia = it }
            }
    }
}
