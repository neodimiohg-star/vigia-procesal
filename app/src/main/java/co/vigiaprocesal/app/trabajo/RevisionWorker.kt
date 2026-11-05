package co.vigiaprocesal.app.trabajo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.vigiaprocesal.app.data.Revisor

/** Revisión en segundo plano: consulta la CPNU y avisa si hay actuaciones nuevas. */
class RevisionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Android da unos 10 minutos por corrida; lo que falte queda para la siguiente.
        val resultado = Revisor(applicationContext).revisarTodos(limiteMs = 8 * 60_000L)
        Notificador.avisar(applicationContext, resultado.novedades)
        return Result.success()
    }
}
