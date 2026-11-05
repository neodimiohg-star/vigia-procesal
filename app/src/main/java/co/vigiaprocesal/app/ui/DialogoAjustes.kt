package co.vigiaprocesal.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun DialogoAjustes(vm: VigiaViewModel, alCerrar: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = alCerrar,
        confirmButton = { TextButton(onClick = alCerrar) { Text("Listo") } },
        title = { Text("Ajustes") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Revisión automática", style = MaterialTheme.typography.titleSmall)
                for (h in listOf(3, 6, 12, 24)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.cambiarFrecuencia(h) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vm.horas == h, onClick = { vm.cambiarFrecuencia(h) })
                        Text("Cada $h horas")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Solo con Wi-Fi", modifier = Modifier.weight(1f))
                    Switch(checked = vm.soloWifi, onCheckedChange = { vm.cambiarSoloWifi(it) })
                }
                Text(
                    "Android decide la hora exacta de cada revisión; puede correrse unos minutos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                Text(
                    "Algunos teléfonos (Xiaomi, Huawei, Oppo, Samsung…) detienen las tareas en segundo plano. " +
                        "Si no te llegan avisos, quita la optimización de batería para Vigía Procesal.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { abrirAjustesBateria(context) }) { Text("Optimización de batería") }
                TextButton(onClick = { compartir(context, vm.textoParaCompartir()) }) {
                    Text("Compartir lista de radicados")
                }

                HorizontalDivider()

                Text(
                    "Vigía Procesal consulta la CPNU de la Rama Judicial; no es una fuente oficial ni reemplaza " +
                        "la revisión de estados, traslados y notificaciones del despacho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun abrirAjustesBateria(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
            )
        } catch (e2: ActivityNotFoundException) {
            // Sin pantalla de ajustes disponible en este teléfono.
        }
    }
}

private fun compartir(context: Context, texto: String) {
    if (texto.isBlank()) return
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    context.startActivity(Intent.createChooser(envio, "Compartir radicados"))
}
