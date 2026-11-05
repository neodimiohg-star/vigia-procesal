package co.vigiaprocesal.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.vigiaprocesal.app.data.Actuacion
import co.vigiaprocesal.app.data.Almacen
import co.vigiaprocesal.app.data.Cpnu
import co.vigiaprocesal.app.data.CpnuException
import co.vigiaprocesal.app.data.Estado
import co.vigiaprocesal.app.data.ProcesoResumen
import co.vigiaprocesal.app.data.ProcesoSeguido
import co.vigiaprocesal.app.data.ResultadoRevision
import co.vigiaprocesal.app.data.Revisor
import co.vigiaprocesal.app.data.Sujeto
import co.vigiaprocesal.app.data.ordenarActuaciones
import co.vigiaprocesal.app.trabajo.Ajustes
import co.vigiaprocesal.app.trabajo.Notificador
import co.vigiaprocesal.app.trabajo.Programador
import co.vigiaprocesal.app.util.formatearRadicado
import co.vigiaprocesal.app.util.soloDigitos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Pantalla {
    data object Lista : Pantalla
    data object Agregar : Pantalla
    data class Detalle(val id: Long) : Pantalla
}

sealed interface Busqueda {
    data object Inactiva : Busqueda
    data class EnCurso(val texto: String) : Busqueda
    data class Resultados(val numero: String, val procesos: List<ProcesoResumen>) : Busqueda
    data class Error(val mensaje: String) : Busqueda
    data class Lote(val agregados: Int, val yaSeguidos: Int, val fallidos: List<String>) : Busqueda
}

/** Nombre corto para mostrar: el alias, o las dos primeras partes, o el radicado. */
fun nombreProceso(p: ProcesoSeguido): String {
    if (p.alias.isNotBlank()) return p.alias
    val partes = p.sujetos.split('|').map { it.substringAfter(':').trim() }.filter { it.isNotBlank() }
    return when {
        partes.size >= 2 -> "${partes[0]} vs. ${partes[1]}"
        partes.size == 1 -> partes[0]
        else -> formatearRadicado(p.llave)
    }
}

private fun textoError(e: Exception): String =
    if (e is CpnuException) e.message ?: "Error al consultar la Rama Judicial."
    else "Error inesperado: ${e.message ?: e.javaClass.simpleName}"

class VigiaViewModel(private val app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen.get(app)
    private val revisor = Revisor(app)
    private val ajustes = Ajustes(app)

    val estado: StateFlow<Estado> = almacen.estado

    var pantalla by mutableStateOf<Pantalla>(Pantalla.Lista)
        private set
    var progresoRevision by mutableStateOf<String?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)
    var busqueda by mutableStateOf<Busqueda>(Busqueda.Inactiva)
        private set
    var horas by mutableIntStateOf(ajustes.horas)
        private set
    var soloWifi by mutableStateOf(ajustes.soloWifi)
        private set

    // Pantalla de detalle
    var actuaciones by mutableStateOf<List<Actuacion>>(emptyList())
        private set
    var resaltadas by mutableStateOf<Set<Long>>(emptySet())
        private set
    var sujetos by mutableStateOf<List<Sujeto>>(emptyList())
        private set
    var cargandoDetalle by mutableStateOf(false)
        private set

    private var trabajoDetalle: Job? = null

    // ----------------------------------------------------------- navegación

    fun irALista() {
        trabajoDetalle?.cancel()
        cargandoDetalle = false
        pantalla = Pantalla.Lista
    }

    fun irAAgregar() {
        if (busqueda !is Busqueda.EnCurso) busqueda = Busqueda.Inactiva
        pantalla = Pantalla.Agregar
    }

    fun volver() {
        val b = busqueda
        if (pantalla is Pantalla.Agregar && (b is Busqueda.Resultados || b is Busqueda.Error)) {
            busqueda = Busqueda.Inactiva
        } else {
            irALista()
        }
    }

    // ------------------------------------------------------------ revisión

    fun revisarAhora() {
        if (progresoRevision != null) return
        if (estado.value.procesos.isEmpty()) {
            mensaje = "Primero agrega un proceso."
            return
        }
        progresoRevision = "Revisando…"
        viewModelScope.launch {
            try {
                val r = revisor.revisarTodos { i, n -> progresoRevision = "Revisando $i de $n en la CPNU…" }
                mensaje = resumen(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = textoError(e)
            } finally {
                progresoRevision = null
            }
        }
    }

    private fun resumen(r: ResultadoRevision): String {
        val nuevas = r.novedades.sumOf { it.nuevas.size }
        return buildString {
            append(
                when {
                    nuevas == 0 -> "Sin actuaciones nuevas"
                    nuevas == 1 -> "1 actuación nueva"
                    else -> "$nuevas actuaciones nuevas"
                },
            )
            append(" · ${r.revisados} de ${r.total} revisados")
            if (r.errores > 0) append(" · ${r.errores} con error")
            if (r.bloqueado) append(". La Rama limitó las consultas; intenta más tarde")
        }
    }

    // ------------------------------------------------------------- agregar

    fun nuevaBusqueda() {
        busqueda = Busqueda.Inactiva
    }

    fun yaSeguido(idProceso: Long): Boolean = almacen.proceso(idProceso) != null

    fun buscar(texto: String) {
        if (busqueda is Busqueda.EnCurso) return
        val piezas = texto.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val validos = piezas.map { soloDigitos(it) }.filter { it.length == 23 }.distinct()
        val invalidos = piezas.filter { soloDigitos(it).length != 23 }

        if (validos.isEmpty()) {
            busqueda = Busqueda.Error(
                "El número de radicación debe tener 23 dígitos. Puedes escribirlo con o sin guiones.",
            )
            return
        }

        if (validos.size == 1 && invalidos.isEmpty()) {
            val numero = validos[0]
            busqueda = Busqueda.EnCurso("Consultando la CPNU… (puede tardar hasta 20 segundos)")
            viewModelScope.launch {
                busqueda = try {
                    val r = Cpnu.buscarPorRadicado(numero)
                    if (r.isEmpty()) {
                        Busqueda.Error(
                            "La CPNU no devolvió ningún proceso con el radicado ${formatearRadicado(numero)}. " +
                                "Verifica el número; algunos despachos no publican en la CPNU.",
                        )
                    } else {
                        Busqueda.Resultados(numero, r)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Busqueda.Error(textoError(e))
                }
            }
            return
        }

        // Varios radicados: se agregan todos los registros públicos que devuelva la CPNU.
        viewModelScope.launch {
            var agregados = 0
            var yaSeguidos = 0
            val fallidos = invalidos.map { "$it (no tiene 23 dígitos)" }.toMutableList()
            for ((i, numero) in validos.withIndex()) {
                busqueda = Busqueda.EnCurso("Agregando ${i + 1} de ${validos.size}: ${formatearRadicado(numero)}")
                try {
                    val r = Cpnu.buscarPorRadicado(numero)
                    val publicos = r.filter { it.esPrivado != true }
                    when {
                        r.isEmpty() -> fallidos += "${formatearRadicado(numero)} (sin resultados en la CPNU)"
                        publicos.isEmpty() -> fallidos += "${formatearRadicado(numero)} (proceso reservado)"
                    }
                    for (p in publicos) {
                        if (revisor.agregar(p, "")) agregados++ else yaSeguidos++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallidos += "${formatearRadicado(numero)} (${textoError(e)})"
                    if (e is CpnuException && e.bloqueo) {
                        validos.drop(i + 1).forEach { fallidos += "${formatearRadicado(it)} (no se alcanzó a consultar)" }
                        break
                    }
                }
            }
            busqueda = Busqueda.Lote(agregados, yaSeguidos, fallidos)
        }
    }

    fun seguir(r: ProcesoResumen, alias: String) {
        if (busqueda is Busqueda.EnCurso) return
        busqueda = Busqueda.EnCurso("Descargando las actuaciones del proceso…")
        viewModelScope.launch {
            try {
                val agregado = revisor.agregar(r, alias)
                mensaje = if (agregado) {
                    "Proceso agregado: ${formatearRadicado(r.llaveProceso)}"
                } else {
                    "Ese proceso ya estaba en tu lista."
                }
                busqueda = Busqueda.Inactiva
                pantalla = Pantalla.Lista
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busqueda = Busqueda.Error(textoError(e))
            }
        }
    }

    // ------------------------------------------------------------- detalle

    fun abrirDetalle(id: Long) {
        val p = almacen.proceso(id)
        if (p == null) {
            mensaje = "Ese proceso ya no está en tu lista."
            return
        }
        trabajoDetalle?.cancel()
        pantalla = Pantalla.Detalle(id)
        resaltadas = p.nuevas
        sujetos = emptyList()
        actuaciones = emptyList()
        cargandoDetalle = false
        trabajoDetalle = viewModelScope.launch {
            actuaciones = ordenarActuaciones(almacen.leerActuaciones(id))
            if (p.nuevas.isNotEmpty()) {
                almacen.actualizarProceso(id) { it.copy(nuevas = emptySet()) }
            }
            Notificador.cancelar(app, id)
            // Se completa la ficha la primera vez y se refresca si la última revisión tiene más de una hora.
            val viejo = System.currentTimeMillis() - p.ultimaRevision > 60 * 60_000L
            if (p.clase.isBlank() || viejo) actualizarDetalle(id)
        }
    }

    fun refrescarDetalle() {
        val id = (pantalla as? Pantalla.Detalle)?.id ?: return
        if (cargandoDetalle) return
        trabajoDetalle?.cancel()
        trabajoDetalle = viewModelScope.launch { actualizarDetalle(id) }
    }

    private suspend fun actualizarDetalle(id: Long) {
        cargandoDetalle = true
        try {
            val d = Cpnu.detalle(id)
            almacen.actualizarProceso(id) { p ->
                p.copy(
                    despacho = d.despacho?.trim()?.takeIf { it.isNotEmpty() } ?: p.despacho,
                    ponente = d.ponente?.trim().orEmpty(),
                    clase = listOfNotNull(d.tipoProceso, d.claseProceso, d.subclaseProceso)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    recurso = d.recurso?.trim().orEmpty(),
                    ubicacion = d.ubicacion?.trim().orEmpty(),
                    contenido = d.contenidoRadicacion?.trim().orEmpty(),
                    fechaRadicacion = d.fechaProceso ?: p.fechaRadicacion,
                )
            }
            sujetos = Cpnu.sujetos(id)
            val nuevas = revisor.revisarUno(id, marcarVistas = true)
            if (nuevas.isNotEmpty()) resaltadas = resaltadas + nuevas.map { it.idRegActuacion }
            actuaciones = ordenarActuaciones(almacen.leerActuaciones(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mensaje = textoError(e)
        } finally {
            cargandoDetalle = false
        }
    }

    fun cambiarAlias(id: Long, alias: String) {
        viewModelScope.launch { almacen.actualizarProceso(id) { it.copy(alias = alias.trim()) } }
    }

    fun eliminar(id: Long) {
        irALista()
        viewModelScope.launch {
            almacen.actualizar { e -> e.copy(procesos = e.procesos.filterNot { it.idProceso == id }) }
            almacen.borrarActuaciones(id)
            Notificador.cancelar(app, id)
        }
        mensaje = "Dejaste de seguir el proceso."
    }

    // ------------------------------------------------------------- ajustes

    fun cambiarFrecuencia(h: Int) {
        ajustes.horas = h
        horas = h
        Programador.programar(app, reemplazar = true)
    }

    fun cambiarSoloWifi(valor: Boolean) {
        ajustes.soloWifi = valor
        soloWifi = valor
        Programador.programar(app, reemplazar = true)
    }

    fun textoParaCompartir(): String = estado.value.procesos.joinToString("\n") { p ->
        formatearRadicado(p.llave) + if (p.alias.isNotBlank()) " — ${p.alias}" else ""
    }
}
