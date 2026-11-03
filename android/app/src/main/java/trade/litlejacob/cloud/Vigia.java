package trade.litlejacob.cloud;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

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
            try {
                revisarPanel(getSharedPreferences("cfg", MODE_PRIVATE));
            } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    /** Avisa si el conector deja de responder o si se cierra alguna sesión. */
    private void revisarPanel(SharedPreferences p) throws Exception {
        Red.Respuesta r = Red.panel("GET", "estado", p.getString("pass", ""));
        if (r.codigo != 200) return;
        JSONObject j = new JSONObject(r.cuerpo);
        SharedPreferences.Editor e = p.edit();

        boolean bien = j.optBoolean("conector") && j.optBoolean("chrome");
        int antes = p.getInt("con_fallos", 0);
        int fallos = bien ? 0 : antes + 1;
        if (fallos == 2) {
            Notifs.mostrar(this, 3, "Claude no puede usar tu Chrome nube",
                    "El conector o Chrome no responden desde hace un rato. Abre LitleJacob Cloud y toca Reiniciar Chrome.");
        } else if (bien && antes >= 2) {
            Notifs.mostrar(this, 3, "Chrome nube funcionando de nuevo", "El conector volvió a responder.");
        }
        e.putInt("con_fallos", fallos);

        JSONArray ses = j.optJSONArray("sesiones");
        if (ses != null) {
            for (int i = 0; i < ses.length(); i++) {
                JSONObject s = ses.optJSONObject(i);
                if (s == null) continue;
                String clave = "ses_" + s.optString("id");
                boolean activa = s.optBoolean("activa");
                if (p.contains(clave) && p.getBoolean(clave, false) && !activa) {
                    Notifs.mostrar(this, 10 + i, "Se cerró tu sesión de " + s.optString("nombre"),
                            "Entra con Controlar pantalla en LitleJacob Cloud para volver a iniciar sesión.");
                }
                e.putBoolean(clave, activa);
            }
        }
        e.apply();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
