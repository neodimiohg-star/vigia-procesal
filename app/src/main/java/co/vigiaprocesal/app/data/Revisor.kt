package co.vigiaprocesal.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException

data class Novedad(val proceso: ProcesoSeguido, val nuevas: List<Actuacion>)

data class ResultadoRevision(
    val total: Int,
    val revisados: Int,
    val novedades: List<Novedad>,
    val errores: Int,
    val bloqueado: Boolean,
)

/** La actuación más reciente. No se toma la primera: algunos despachos no las envían ordenadas. */
fun masReciente(lista: List<Actuacion>): Actuacion? = lista.maxWithOrNull(
    compareBy<Actuacion> { it.fechaActuacion.orEmpty() }
        .thenBy { it.consActuacion ?: 0 }
        .thenBy { it.idRegActuacion },
)

fun ordenarActuaciones(lista: List<Actuacion>): List<Actuacion> = lista.sortedWith(
    compareByDescending<Actuacion> { it.fechaActuacion.orEmpty() }
        .thenByDescending { it.consActuacion ?: 0 }
        .thenByDescending { it.idRegActuacion },
)

/** Agrega procesos y detecta actuaciones nuevas comparando contra las ya conocidas. */
class Revisor(context: Context) {
    private val almacen = Almacen.get(context)

    /** Empieza a seguir un proceso. Devuelve false si ya estaba en la lista. */
    suspend fun agregar(r: ProcesoResumen, alias: String): Boolean {
        if (almacen.proceso(r.idProceso) != null) return false
        val (lista, total) = Cpnu.todasLasActuaciones(r.idProceso)
        val reciente = masReciente(lista)
        val ahora = System.currentTimeMillis()
        almacen.guardarActuaciones(r.idProceso, lista)
        val nuevo = ProcesoSeguido(
            idProceso = r.idProceso,
            llave = r.llaveProceso.trim(),
            alias = alias.trim(),
            despacho = r.despacho?.trim().orEmpty(),
            departamento = r.departamento?.trim().orEmpty(),
            sujetos = r.sujetosProcesales?.trim().orEmpty(),
            fechaRadicacion = r.fechaProceso,
            fechaUltimaActuacion = reciente?.fechaActuacion ?: r.fechaUltimaActuacion,
            ultimaActuacion = reciente?.actuacion?.trim(),
            total = total,
            conocidas = lista.map { it.idRegActuacion }.toSet(),
            inicializado = true,
            ultimaRevision = ahora,
            agregado = ahora,
        )
        var agregado = false
        almacen.actualizar { e ->
            if (e.procesos.any { it.idProceso == nuevo.idProceso }) {
                e
            } else {
                agregado = true
                e.copy(procesos = listOf(nuevo) + e.procesos)
            }
        }
        return agregado
    }

    /**
     * Revisa un proceso. Primero trae solo la primera página de actuaciones; si
     * el total cambió o aparece una actuación desconocida, descarga la lista
     * completa. Devuelve las actuaciones nuevas.
     */
    suspend fun revisarUno(id: Long, marcarVistas: Boolean = false): List<Actuacion> {
        val actual = almacen.proceso(id) ?: return emptyList()
        val primera = Cpnu.actuacionesPagina(id, 1)
        val total = primera.paginacion?.cantidadRegistros ?: primera.actuaciones.size
        val hayCambio = !actual.inicializado ||
            total != actual.total ||
            primera.actuaciones.any { it.idRegActuacion !in actual.conocidas } ||
            !almacen.tieneActuaciones(id)
        val ahora = System.currentTimeMillis()

        if (!hayCambio) {
            almacen.actualizarProceso(id) {
                it.copy(
                    ultimaRevision = ahora,
                    error = null,
                    nuevas = if (marcarVistas) emptySet() else it.nuevas,
                )
            }
            return emptyList()
        }

        val (lista, totalFinal) = Cpnu.todasLasActuaciones(id, primera)
        val nuevas = if (actual.inicializado) lista.filter { it.idRegActuacion !in actual.conocidas } else emptyList()
        val reciente = masReciente(lista)
        almacen.guardarActuaciones(id, lista)
        almacen.actualizarProceso(id) { p ->
            p.copy(
                total = totalFinal,
                conocidas = p.conocidas + lista.map { it.idRegActuacion },
                nuevas = if (marcarVistas) emptySet() else p.nuevas + nuevas.map { it.idRegActuacion },
                fechaUltimaActuacion = reciente?.fechaActuacion ?: p.fechaUltimaActuacion,
                ultimaActuacion = reciente?.actuacion?.trim() ?: p.ultimaActuacion,
                inicializado = true,
                ultimaRevision = ahora,
                error = null,
            )
        }
        return nuevas
    }

    /**
     * Revisa todos los procesos, empezando por los que llevan más tiempo sin
     * revisarse. Se detiene si la Rama bloquea las consultas o si se agota el
     * tiempo disponible (los que falten se revisan en la siguiente corrida).
     */
    suspend fun revisarTodos(
        limiteMs: Long = Long.MAX_VALUE,
        progreso: (Int, Int) -> Unit = { _, _ -> },
    ): ResultadoRevision {
        val inicio = System.currentTimeMillis()
        val lista = almacen.estado.value.procesos.sortedBy { it.ultimaRevision }
        val novedades = mutableListOf<Novedad>()
        var revisados = 0
        var errores = 0
        var bloqueado = false

        for ((i, p) in lista.withIndex()) {
            if (System.currentTimeMillis() - inicio > limiteMs) break
            progreso(i + 1, lista.size)
            try {
                val nuevas = revisarUno(p.idProceso)
                revisados++
                if (nuevas.isNotEmpty()) {
                    novedades += Novedad(almacen.proceso(p.idProceso) ?: p, nuevas)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errores++
                val mensaje = (e as? CpnuException)?.message ?: "Error inesperado: ${e.message ?: e.javaClass.simpleName}"
                almacen.actualizarProceso(p.idProceso) {
                    it.copy(error = mensaje, ultimaRevision = System.currentTimeMillis())
                }
                if (e is CpnuException && e.bloqueo) {
                    bloqueado = true
                    break
                }
            }
        }
        almacen.actualizar { it.copy(ultimaRevisionGeneral = System.currentTimeMillis()) }
        return ResultadoRevision(lista.size, revisados, novedades, errores, bloqueado)
    }
}
