package co.vigiaprocesal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.vigiaprocesal.app.data.ProcesoResumen
import co.vigiaprocesal.app.util.fechaCorta
import co.vigiaprocesal.app.util.formatearRadicado
import co.vigiaprocesal.app.util.soloDigitos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAgregar(vm: VigiaViewModel, snackbar: SnackbarHostState) {
    var texto by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    val b = vm.busqueda

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar proceso") },
                colors = coloresBarra(),
                navigationIcon = {
                    IconButton(onClick = { vm.irALista() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { relleno ->
        Column(
            Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (b) {
                is Busqueda.Resultados -> Resultados(b, alias, vm)
                is Busqueda.Lote -> ResumenLote(b, vm)
                else -> {
                    val ocupado = b is Busqueda.EnCurso
                    Text(
                        "Escribe el número de radicación de 23 dígitos, con o sin guiones. " +
                            "Si pegas varios (uno por línea), se agregan todos de una vez.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Número de radicación") },
                        placeholder = { Text("11001-31-03-015-2019-00123-00") },
                        supportingText = {
                            if (!texto.contains('\n')) Text("${soloDigitos(texto).length} de 23 dígitos")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !ocupado,
                        minLines = 1,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Nombre para identificarlo (opcional)") },
                        placeholder = { Text("Ej.: Pertenencia Calle 91A") },
                        singleLine = true,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { vm.buscar(texto) },
                        enabled = texto.isNotBlank() && !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Buscar en la CPNU")
                    }
                    when (b) {
                        is Busqueda.EnCurso -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(b.texto, style = MaterialTheme.typography.bodyMedium)
                        }
                        is Busqueda.Error -> Text(
                            b.mensaje,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }
                    Text(
                        "La consulta se hace directamente a la Consulta de Procesos Nacional Unificada " +
                            "(CPNU) de la Rama Judicial. Su servidor suele tardar entre 5 y 20 segundos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Resultados(b: Busqueda.Resultados, alias: String, vm: VigiaViewModel) {
    Text(
        if (b.procesos.size == 1) {
            "Se encontró 1 registro en la CPNU:"
        } else {
            "La CPNU tiene ${b.procesos.size} registros con este radicado (por ejemplo, distintas " +
                "instancias o despachos). Elige cuál seguir; puedes agregar más de uno:"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    for (r in b.procesos) TarjetaResultado(r, alias, vm)
    OutlinedButton(onClick = { vm.nuevaBusqueda() }, modifier = Modifier.fillMaxWidth()) {
        Text("Buscar otro radicado")
    }
}

@Composable
private fun TarjetaResultado(r: ProcesoResumen, alias: String, vm: VigiaViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatearRadicado(r.llaveProceso),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            r.despacho?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            r.departamento?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.sujetosProcesales?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it.replace(" | ", "\n"), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Radicado: ${fechaCorta(r.fechaProceso)} · Última actuación: ${fechaCorta(r.fechaUltimaActuacion)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                r.esPrivado == true -> Text(
                    "Proceso reservado: la CPNU no publica sus actuaciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                vm.yaSeguido(r.idProceso) -> Text(
                    "Ya sigues este proceso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Button(onClick = { vm.seguir(r, alias) }) { Text("Seguir este proceso") }
            }
        }
    }
}

@Composable
private fun ResumenLote(b: Busqueda.Lote, vm: VigiaViewModel) {
    Text("Resultado", style = MaterialTheme.typography.titleMedium)
    Text(
        buildString {
            append(if (b.agregados == 1) "1 proceso agregado." else "${b.agregados} procesos agregados.")
            if (b.yaSeguidos > 0) append(" ${b.yaSeguidos} ya estaban en tu lista.")
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (b.fallidos.isNotEmpty()) {
        Text("No se pudieron agregar:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        for (f in b.fallidos) {
            Text("• $f", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Button(onClick = { vm.irALista() }, modifier = Modifier.fillMaxWidth()) { Text("Ver mis procesos") }
    OutlinedButton(onClick = { vm.nuevaBusqueda() }, modifier = Modifier.fillMaxWidth()) { Text("Agregar más") }
}
