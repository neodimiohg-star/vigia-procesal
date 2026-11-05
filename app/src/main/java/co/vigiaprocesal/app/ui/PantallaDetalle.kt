package co.vigiaprocesal.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import co.vigiaprocesal.app.data.Actuacion
import co.vigiaprocesal.app.data.Cpnu
import co.vigiaprocesal.app.data.ProcesoSeguido
import co.vigiaprocesal.app.data.Sujeto
import co.vigiaprocesal.app.util.fechaCorta
import co.vigiaprocesal.app.util.fechaHora
import co.vigiaprocesal.app.util.formatearRadicado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(vm: VigiaViewModel, p: ProcesoSeguido, snackbar: SnackbarHostState) {
    var editarAlias by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(nombreProceso(p), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = coloresBarra(),
                navigationIcon = {
                    IconButton(onClick = { vm.irALista() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refrescarDetalle() }, enabled = !vm.cargandoDetalle) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Consultar ahora")
                    }
                    IconButton(onClick = { editarAlias = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Cambiar nombre")
                    }
                    IconButton(onClick = { confirmarBorrado = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Dejar de seguir")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.cargandoDetalle) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Consultando la CPNU…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { FichaProceso(vm, p, vm.sujetos) }
            item {
                Text(
                    "Actuaciones (${maxOf(p.total, vm.actuaciones.size)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (vm.actuaciones.isEmpty()) {
                item {
                    Text(
                        if (vm.cargandoDetalle) "Cargando actuaciones…" else "La CPNU no registra actuaciones para este proceso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.actuaciones) { a ->
                TarjetaActuacion(a, nueva = a.idRegActuacion in vm.resaltadas)
            }
            item {
                Text(
                    "Información tomada de la Consulta de Procesos Nacional Unificada de la Rama Judicial. " +
                        "Antes de actuar sobre un término, verifica en el portal oficial y en los estados del despacho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (editarAlias) {
        var texto by remember { mutableStateOf(p.alias) }
        AlertDialog(
            onDismissRequest = { editarAlias = false },
            title = { Text("Nombre del proceso") },
            text = {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Nombre para identificarlo") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.cambiarAlias(p.idProceso, texto)
                    editarAlias = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editarAlias = false }) { Text("Cancelar") } },
        )
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Dejar de seguir este proceso?") },
            text = { Text("Se quitará de la lista y no recibirás más avisos de ${formatearRadicado(p.llave)}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrado = false
                    vm.eliminar(p.idProceso)
                }) { Text("Dejar de seguir") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FichaProceso(vm: VigiaViewModel, p: ProcesoSeguido, sujetos: List<Sujeto>) {
    val context = LocalContext.current
    val portapapeles = LocalClipboardManager.current

    val textoSujetos = if (sujetos.isNotEmpty()) {
        sujetos.joinToString("\n") { s ->
            val tipo = s.tipoSujeto?.trim().orEmpty()
            val nombre = s.nombreRazonSocial?.trim().orEmpty()
            val emplazado = if (s.esEmplazado == true) " (emplazado)" else ""
            if (tipo.isNotEmpty()) "$tipo: $nombre$emplazado" else "$nombre$emplazado"
        }
    } else {
        p.sujetos.replace(" | ", "\n")
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatearRadicado(p.llave),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Campo("Despacho", p.despacho)
            Campo("Ponente", p.ponente)
            Campo("Tipo · clase · subclase", p.clase)
            Campo("Recurso", p.recurso)
            Campo("Ubicación del expediente", p.ubicacion)
            Campo("Fecha de radicación", p.fechaRadicacion?.let { fechaCorta(it) })
            Campo("Sujetos procesales", textoSujetos)
            Campo("Contenido de la radicación", p.contenido)
            Campo("Última consulta a la CPNU", fechaHora(p.ultimaRevision))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    portapapeles.setText(AnnotatedString(p.llave))
                    vm.mensaje = "Radicado copiado"
                }) { Text("Copiar radicado") }
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Cpnu.PAGINA_WEB.toUri()))
                    } catch (e: ActivityNotFoundException) {
                        vm.mensaje = "No hay un navegador disponible."
                    }
                }) { Text("Abrir CPNU") }
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String?) {
    if (valor.isNullOrBlank()) return
    Column {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TarjetaActuacion(a: Actuacion, nueva: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (nueva) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fechaCorta(a.fechaActuacion),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (a.conDocumentos == true) {
                    Text("📎 Con documentos", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                }
                if (nueva) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "NUEVA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            a.actuacion?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall)
            }
            a.anotacion?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            val fechas = listOfNotNull(
                a.fechaInicial?.let { "Inicia término: ${fechaCorta(it)}" },
                a.fechaFinal?.let { "Finaliza término: ${fechaCorta(it)}" },
                a.fechaRegistro?.let { "Registro: ${fechaCorta(it)}" },
            )
            if (fechas.isNotEmpty()) {
                Text(
                    fechas.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
