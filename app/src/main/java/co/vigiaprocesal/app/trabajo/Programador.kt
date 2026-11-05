package co.vigiaprocesal.app.trabajo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Programa la revisión automática periódica con WorkManager. */
object Programador {
    private const val NOMBRE = "revision_periodica"

    fun programar(context: Context, reemplazar: Boolean = false) {
        val ajustes = Ajustes(context)
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(if (ajustes.soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val peticion = PeriodicWorkRequestBuilder<RevisionWorker>(ajustes.horas.toLong(), TimeUnit.HOURS)
            .setConstraints(restricciones)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOMBRE,
            if (reemplazar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            peticion,
        )
    }
}
