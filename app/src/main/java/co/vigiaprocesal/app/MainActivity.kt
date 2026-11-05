package co.vigiaprocesal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import co.vigiaprocesal.app.trabajo.Notificador
import co.vigiaprocesal.app.ui.AppVigia
import co.vigiaprocesal.app.ui.TemaVigia
import co.vigiaprocesal.app.ui.VigiaViewModel

class MainActivity : ComponentActivity() {
    private val vm: VigiaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) abrirDesdeNotificacion(intent)
        setContent {
            TemaVigia {
                AppVigia(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeNotificacion(intent)
    }

    private fun abrirDesdeNotificacion(intent: Intent?) {
        val id = intent?.getLongExtra(Notificador.EXTRA_PROCESO, -1L) ?: -1L
        if (id > 0) vm.abrirDetalle(id)
    }
}
