package co.vigiaprocesal.app.data

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Respuestas de la API de la CPNU (Consulta de Procesos Nacional Unificada).
// Todos los campos son opcionales: la API no siempre los envía y se ignoran
// los que no se usan.
// ---------------------------------------------------------------------------

@Serializable
data class Paginacion(
    val cantidadRegistros: Int? = null,
    val registrosPagina: Int? = null,
    val cantidadPaginas: Int? = null,
    val pagina: Int? = null,
)

@Serializable
data class RespuestaConsulta(
    val procesos: List<ProcesoResumen> = emptyList(),
    val paginacion: Paginacion? = null,
)

@Serializable
data class ProcesoResumen(
    val idProceso: Long = 0,
    val llaveProceso: String = "",
    val fechaProceso: String? = null,
    val fechaUltimaActuacion: String? = null,
    val despacho: String? = null,
    val departamento: String? = null,
    val sujetosProcesales: String? = null,
    val esPrivado: Boolean? = null,
)

@Serializable
data class ProcesoDetalle(
    val llaveProceso: String? = null,
    val esPrivado: Boolean? = null,
    val fechaProceso: String? = null,
    val despacho: String? = null,
    val ponente: String? = null,
    val tipoProceso: String? = null,
    val claseProceso: String? = null,
    val subclaseProceso: String? = null,
    val recurso: String? = null,
    val ubicacion: String? = null,
    val contenidoRadicacion: String? = null,
)

@Serializable
data class RespuestaActuaciones(
    val actuaciones: List<Actuacion> = emptyList(),
    val paginacion: Paginacion? = null,
)

@Serializable
data class Actuacion(
    val idRegActuacion: Long = 0,
    val consActuacion: Int? = null,
    val fechaActuacion: String? = null,
    val actuacion: String? = null,
    val anotacion: String? = null,
    val fechaInicial: String? = null,
    val fechaFinal: String? = null,
    val fechaRegistro: String? = null,
    val conDocumentos: Boolean? = null,
)

@Serializable
data class RespuestaSujetos(
    val sujetos: List<Sujeto> = emptyList(),
)

@Serializable
data class Sujeto(
    val tipoSujeto: String? = null,
    val esEmplazado: Boolean? = null,
    val identificacion: String? = null,
    val nombreRazonSocial: String? = null,
)

// ---------------------------------------------------------------------------
// Lo que la app guarda en el teléfono.
// ---------------------------------------------------------------------------

@Serializable
data class ProcesoSeguido(
    val idProceso: Long,
    val llave: String,
    val alias: String = "",
    val despacho: String = "",
    val departamento: String = "",
    val sujetos: String = "",
    val ponente: String = "",
    val clase: String = "",
    val recurso: String = "",
    val ubicacion: String = "",
    val contenido: String = "",
    val fechaRadicacion: String? = null,
    val fechaUltimaActuacion: String? = null,
    val ultimaActuacion: String? = null,
    /** Total de actuaciones según la paginación de la CPNU. */
    val total: Int = 0,
    /** Identificadores (idRegActuacion) de las actuaciones ya conocidas. */
    val conocidas: Set<Long> = emptySet(),
    /** Actuaciones nuevas que el usuario todavía no ha visto. */
    val nuevas: Set<Long> = emptySet(),
    /** true cuando ya se descargó la lista completa al menos una vez. */
    val inicializado: Boolean = false,
    val ultimaRevision: Long = 0,
    val error: String? = null,
    val agregado: Long = 0,
)

@Serializable
data class Estado(
    val procesos: List<ProcesoSeguido> = emptyList(),
    val ultimaRevisionGeneral: Long = 0,
)
