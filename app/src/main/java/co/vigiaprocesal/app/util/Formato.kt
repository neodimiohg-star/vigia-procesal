package co.vigiaprocesal.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val localeCo: Locale = Locale.forLanguageTag("es-CO")
private val formatoFechaHora: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", localeCo)

fun soloDigitos(texto: String): String = texto.filter { it.isDigit() }

/**
 * 23 dígitos -> 11001-31-03-015-2019-00123-00
 * (municipio, entidad, especialidad, despacho, año, consecutivo, recurso).
 */
fun formatearRadicado(llave: String): String {
    val d = soloDigitos(llave)
    if (d.length != 23) return llave
    return listOf(
        d.substring(0, 5),
        d.substring(5, 7),
        d.substring(7, 9),
        d.substring(9, 12),
        d.substring(12, 16),
        d.substring(16, 21),
        d.substring(21, 23),
    ).joinToString("-")
}

/** "2025-06-13T00:00:00" -> "13/06/2025". Se toma la fecha tal como la envía la CPNU, sin zona horaria. */
fun fechaCorta(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 10) return "—"
    val partes = iso.substring(0, 10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso.take(10)
}

fun fechaHora(epochMs: Long): String =
    if (epochMs <= 0) {
        "nunca"
    } else {
        formatoFechaHora.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    }
