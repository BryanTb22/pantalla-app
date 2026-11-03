package trade.litlejacob.cloud;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/** Revisa cada 15 minutos si el servidor responde y avisa si se cae o vuelve. */
public class Vigia extends JobService {
    static final int ID = 1;

    static void programar(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        JobInfo ji = new JobInfo.Builder(ID, new ComponentName(c, Vigia.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        try {
            js.schedule(ji);
        } catch (Exception e) {
            // Si el sistema no permite programar la revisión, la app sigue funcionando igual.
        }
    }

    static void cancelar(Context c) {
        try {
            c.getSystemService(JobScheduler.class).cancel(ID);
        } catch (Exception ignored) { }
    }

    /** true = en línea, false = caído, null = no se pudo saber (sin internet en el celular). */
    static Boolean revisar() {
        int code = Red.estado(Red.PANTALLA);
        if (code == -1) return null;
        if (code == 200) return true;
        // Confirmar con un segundo intento para evitar falsas alarmas.
        try { Thread.sleep(20000); } catch (InterruptedException ignored) { }
        code = Red.estado(Red.PANTALLA);
        if (code == -1) return null;
        return code == 200;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            Boolean arriba = revisar();
            if (arriba != null) {
                SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
                boolean antes = p.getBoolean("servidor_ok", true);
                if (antes && !arriba) {
                    Notifs.mostrar(this, "Tu servidor no responde",
                            "La pantalla de LitleJacob Cloud está caída. Revisa el servidor en Oracle Cloud.");
                } else if (!antes && arriba) {
                    Notifs.mostrar(this, "Servidor en línea de nuevo",
                            "Ya puedes volver a usar LitleJacob Cloud.");
                }
                p.edit().putBoolean("servidor_ok", arriba).apply();
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
