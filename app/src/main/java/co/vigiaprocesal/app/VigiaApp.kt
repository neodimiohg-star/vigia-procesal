package co.vigiaprocesal.app

import android.app.Application
import co.vigiaprocesal.app.trabajo.Notificador
import co.vigiaprocesal.app.trabajo.Programador

class VigiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notificador.crearCanal(this)
        Programador.programar(this)
    }
}
