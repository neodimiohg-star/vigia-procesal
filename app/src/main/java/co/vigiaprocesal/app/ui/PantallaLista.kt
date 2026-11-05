package co.vigiaprocesal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.vigiaprocesal.app.data.Estado
import co.vigiaprocesal.app.data.ProcesoSeguido
import co.vigiaprocesal.app.util.fechaCorta
import co.vigiaprocesal.app.util.fechaHora
import co.vigiaprocesal.app.util.formatearRadicado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(vm: VigiaViewModel, estado: Estado, snackbar: SnackbarHostState) {
    var verAjustes by remember { mutableStateOf(false) }
    val progreso = vm.progresoRevision

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vigía Procesal") },
                colors = coloresBarra(),
                actions = {
                    IconButton(onClick = { vm.revisarAhora() }, enabled = progreso == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Revisar ahora")
                    }
                    IconButton(onClick = { verAjustes = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Agregar") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { vm.irAAgregar() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(
            Modifier
                .padding(relleno)
                .fillMaxSize(),
        ) {
            if (progreso != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    progreso,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    "Última revisión: ${fechaHora(estado.ultimaRevisionGeneral)} · automática cada ${vm.horas} h",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (estado.procesos.isEmpty()) {
                ListaVacia()
            } else {
                val ordenados = estado.procesos.sortedWith(
                    compareByDescending<ProcesoSeguido> { it.nuevas.isNotEmpty() }
                        .thenByDescending { it.fechaUltimaActuacion.orEmpty() },
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ordenados, key = { it.idProceso }) { p ->
                        TarjetaProceso(p) { vm.abrirDetalle(p.idProceso) }
                    }
                }
            }
        }
    }

    if (verAjustes) DialogoAjustes(vm) { verAjustes = false }
}

@Composable
private fun TarjetaProceso(p: ProcesoSeguido, alPulsar: () -> Unit) {
    val hayNuevas = p.nuevas.isNotEmpty()
    Card(
        onClick = alPulsar,
        modifier = Modifier.fillMaxWidth(),
        colors = if (hayNuevas) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nombreProceso(p),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hayNuevas) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(if (p.nuevas.size == 1) "1 nueva" else "${p.nuevas.size} nuevas")
                    }
                }
            }
            Text(
                formatearRadicado(p.llave),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (p.despacho.isNotBlank()) {
                Text(
                    p.despacho,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Última actuación: ${fechaCorta(p.fechaUltimaActuacion)}" +
                    (p.ultimaActuacion?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            p.error?.let {
                Text(
                    "⚠ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ListaVacia() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Aún no sigues ningún proceso",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "Pulsa «Agregar» y escribe el número de radicación de 23 dígitos. " +
                    "La app revisará la CPNU automáticamente y te avisará cuando haya actuaciones nuevas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
