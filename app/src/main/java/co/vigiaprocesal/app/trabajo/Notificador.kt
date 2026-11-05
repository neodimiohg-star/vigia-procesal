package co.vigiaprocesal.app.trabajo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.vigiaprocesal.app.MainActivity
import co.vigiaprocesal.app.R
import co.vigiaprocesal.app.data.Novedad
import co.vigiaprocesal.app.ui.nombreProceso
import co.vigiaprocesal.app.util.fechaCorta

object Notificador {
    const val EXTRA_PROCESO = "co.vigiaprocesal.app.PROCESO"
    private const val CANAL = "actuaciones"
    private const val GRUPO = "vigia_actuaciones"
    private const val ID_RESUMEN = 1

    fun crearCanal(context: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Nuevas actuaciones",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Aviso cuando un proceso que sigues tiene actuaciones nuevas en la CPNU"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    private fun puedeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun idNotificacion(idProceso: Long): Int = (idProceso % Int.MAX_VALUE).toInt().let { if (it == ID_RESUMEN) it + 1 else it }

    fun avisar(context: Context, novedades: List<Novedad>) {
        if (novedades.isEmpty() || !puedeNotificar(context)) return
        val nm = NotificationManagerCompat.from(context)
        try {
            for (n in novedades) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_PROCESO, n.proceso.idProceso)
                }
                val pendiente = PendingIntent.getActivity(
                    context,
                    idNotificacion(n.proceso.idProceso),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val titulo = if (n.nuevas.size == 1) "1 actuación nueva" else "${n.nuevas.size} actuaciones nuevas"
                val detalle = n.nuevas.take(5).joinToString("\n") { a ->
                    val anotacion = a.anotacion?.trim()?.takeIf { it.isNotEmpty() }?.let { " — ${it.take(120)}" }.orEmpty()
                    "${fechaCorta(a.fechaActuacion)}: ${a.actuacion?.trim().orEmpty()}$anotacion"
                }
                val notificacion = NotificationCompat.Builder(context, CANAL)
                    .setSmallIcon(R.drawable.ic_notificacion)
                    .setContentTitle("$titulo · ${nombreProceso(n.proceso)}")
                    .setContentText(detalle.lineSequence().firstOrNull().orEmpty())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detalle))
                    .setContentIntent(pendiente)
                    .setAutoCancel(true)
                    .setGroup(GRUPO)
                    .build()
                nm.notify(idNotificacion(n.proceso.idProceso), notificacion)
            }
            if (novedades.size > 1) {
                val resumen = NotificationCompat.Builder(context, CANAL)
                    .setSmallIcon(R.drawable.ic_notificacion)
                    .setContentTitle("Vigía Procesal")
                    .setContentText("${novedades.size} procesos con actuaciones nuevas")
                    .setGroup(GRUPO)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
                nm.notify(ID_RESUMEN, resumen)
            }
        } catch (e: SecurityException) {
            // El usuario retiró el permiso de notificaciones: no hay nada que hacer.
        }
    }

    fun cancelar(context: Context, idProceso: Long) {
        NotificationManagerCompat.from(context).cancel(idNotificacion(idProceso))
    }
}
