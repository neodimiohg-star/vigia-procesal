package co.vigiaprocesal.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Error al consultar la CPNU, con un mensaje en español listo para mostrar. */
class CpnuException(
    mensaje: String,
    val codigo: Int = 0,
    /** true si la Rama está limitando las consultas (429 o bloqueo del firewall). */
    val bloqueo: Boolean = false,
) : Exception(mensaje)

/**
 * Cliente de la API pública de la Consulta de Procesos Nacional Unificada (CPNU)
 * de la Rama Judicial. Corre en el puerto 448 y responde JSON.
 *
 * El servidor es lento (5 a 20 segundos por consulta) y bloquea a quien consulta
 * en ráfagas, por eso todas las consultas pasan por un turno con un espacio
 * mínimo entre una y otra.
 */
object Cpnu {
    private const val BASE = "https://consultaprocesos.ramajudicial.gov.co:448/api/v2"
    const val PAGINA_WEB = "https://consultaprocesos.ramajudicial.gov.co/Procesos/NumeroRadicacion"

    private const val ESPACIO_MS = 1200L
    private const val MAX_PAGINAS = 25

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val turno = Mutex()
    private var ultimaConsulta = 0L

    private suspend fun esperarTurno() {
        turno.withLock {
            val espera = ultimaConsulta + ESPACIO_MS - System.currentTimeMillis()
            if (espera > 0) delay(espera)
            ultimaConsulta = System.currentTimeMillis()
        }
    }

    private sealed interface Respuesta {
        class Ok(val cuerpo: String) : Respuesta
        class Falla(val error: CpnuException, val reintentable: Boolean) : Respuesta
    }

    private suspend fun obtener(ruta: String, intentos: Int = 3): String {
        var ultimoError: CpnuException? = null
        for (intento in 1..intentos) {
            esperarTurno()
            when (val r = withContext(Dispatchers.IO) { ejecutar(ruta) }) {
                is Respuesta.Ok -> return r.cuerpo
                is Respuesta.Falla -> {
                    if (!r.reintentable) throw r.error
                    ultimoError = r.error
                }
            }
            if (intento < intentos) delay(1500L * intento)
        }
        throw ultimoError ?: CpnuException("No se pudo consultar la Rama Judicial.")
    }

    private fun ejecutar(ruta: String): Respuesta {
        val peticion = Request.Builder()
            .url(BASE + ruta)
            .header("Accept", "application/json")
            .header("User-Agent", "VigiaProcesal/1.0 (Android)")
            .build()
        return try {
            cliente.newCall(peticion).execute().use { r ->
                val cuerpo = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    Respuesta.Ok(cuerpo)
                } else {
                    val inicio = cuerpo.trimStart()
                    val esJson = inicio.startsWith("{") || inicio.startsWith("[")
                    val bloqueo = r.code == 429 || (r.code == 403 && !esJson)
                    val mensaje = when {
                        bloqueo -> "La Rama Judicial está limitando las consultas. Se volverá a intentar más tarde."
                        r.code == 404 -> "La CPNU no encontró la información solicitada."
                        else -> mensajeDelServidor(cuerpo)
                            ?: "La Rama Judicial respondió con un error (${r.code})."
                    }
                    Respuesta.Falla(
                        CpnuException(mensaje, r.code, bloqueo),
                        reintentable = !bloqueo && r.code != 400 && r.code != 404,
                    )
                }
            }
        } catch (e: IOException) {
            Respuesta.Falla(CpnuException(mensajeDeRed(e)), reintentable = true)
        }
    }

    private fun mensajeDelServidor(cuerpo: String): String? = runCatching {
        val obj = json.parseToJsonElement(cuerpo).jsonObject
        (obj["Message"] ?: obj["message"])?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun mensajeDeRed(e: IOException): String = when (e) {
        is InterruptedIOException -> "La Rama Judicial no respondió a tiempo."
        is UnknownHostException -> "Sin conexión a internet."
        is SSLException -> "No se pudo establecer una conexión segura con la Rama Judicial."
        else -> "Error de conexión con la Rama Judicial (${e.javaClass.simpleName})."
    }

    private fun <T> decodificar(estrategia: DeserializationStrategy<T>, cuerpo: String): T = try {
        json.decodeFromString(estrategia, cuerpo)
    } catch (e: IllegalArgumentException) {
        throw CpnuException("La Rama Judicial devolvió una respuesta inesperada.")
    }

    /** Busca un radicado de 23 dígitos. Puede devolver varios registros (despachos o instancias). */
    suspend fun buscarPorRadicado(numero: String): List<ProcesoResumen> {
        val cuerpo = try {
            obtener("/Procesos/Consulta/NumeroRadicacion?numero=$numero&SoloActivos=false&pagina=1", intentos = 2)
        } catch (e: CpnuException) {
            if (e.codigo == 404) return emptyList() else throw e
        }
        return decodificar(RespuestaConsulta.serializer(), cuerpo).procesos
    }

    suspend fun detalle(idProceso: Long): ProcesoDetalle =
        decodificar(ProcesoDetalle.serializer(), obtener("/Proceso/Detalle/$idProceso"))

    suspend fun sujetos(idProceso: Long): List<Sujeto> = try {
        decodificar(RespuestaSujetos.serializer(), obtener("/Proceso/Sujetos/$idProceso?pagina=1")).sujetos
    } catch (e: CpnuException) {
        if (e.codigo == 404) emptyList() else throw e
    }

    suspend fun actuacionesPagina(idProceso: Long, pagina: Int): RespuestaActuaciones = try {
        decodificar(RespuestaActuaciones.serializer(), obtener("/Proceso/Actuaciones/$idProceso?pagina=$pagina"))
    } catch (e: CpnuException) {
        if (e.codigo == 404) RespuestaActuaciones() else throw e
    }

    /**
     * Descarga todas las actuaciones (40 por página). Devuelve la lista sin
     * duplicados y el total que reporta la CPNU.
     */
    suspend fun todasLasActuaciones(
        idProceso: Long,
        primera: RespuestaActuaciones? = null,
    ): Pair<List<Actuacion>, Int> {
        val p1 = primera ?: actuacionesPagina(idProceso, 1)
        val total = p1.paginacion?.cantidadRegistros ?: p1.actuaciones.size
        val paginas = p1.paginacion?.cantidadPaginas ?: 1
        val lista = p1.actuaciones.toMutableList()
        var pagina = 2
        while (pagina <= paginas && pagina <= MAX_PAGINAS) {
            val r = actuacionesPagina(idProceso, pagina)
            if (r.actuaciones.isEmpty()) break
            lista += r.actuaciones
            pagina++
        }
        return lista.distinctBy { it.idRegActuacion } to total
    }
}
