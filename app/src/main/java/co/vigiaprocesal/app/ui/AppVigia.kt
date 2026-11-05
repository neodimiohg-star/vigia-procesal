package co.vigiaprocesal.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppVigia(vm: VigiaViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val mensaje = vm.mensaje
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            snackbar.showSnackbar(mensaje)
            vm.mensaje = null
        }
    }

    PedirPermisoNotificaciones()
    BackHandler(enabled = vm.pantalla != Pantalla.Lista) { vm.volver() }

    when (val p = vm.pantalla) {
        Pantalla.Lista -> PantallaLista(vm, estado, snackbar)
        Pantalla.Agregar -> PantallaAgregar(vm, snackbar)
        is Pantalla.Detalle -> {
            val proceso = estado.procesos.firstOrNull { it.idProceso == p.id }
            if (proceso != null) {
                PantallaDetalle(vm, proceso, snackbar)
            } else {
                LaunchedEffect(p.id) { vm.irALista() }
            }
        }
    }
}

@Composable
private fun PedirPermisoNotificaciones() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedido) lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
