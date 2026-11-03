package trade.litlejacob.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Panel de inicio de LitleJacob Cloud. */
public class MainActivity extends Activity {
    static final int FONDO = 0xFF0B1020, TARJETA = 0xFF151B2E, BORDE = 0xFF252D45;
    static final int AZUL = 0xFF2563EB, VIOLETA = 0xFF7C3AED, CIAN = 0xFF22D3EE;
    static final int TEXTO = 0xFFF1F5F9, GRIS = 0xFF94A3B8, VERDE = 0xFF22C55E, ROJO = 0xFFEF4444, AMBAR = 0xFFF59E0B;

    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private View punto;
    private TextView estado, detalle, txtUpd;
    private LinearLayout bannerUpd;
    private int versionRemota = 0;

    private final Runnable ciclo = new Runnable() {
        public void run() {
            revisarServidor();
            h.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);
        setContentView(construir());
        Notifs.crearCanal(this);
        if (prefs.getBoolean("aviso", true)) {
            Vigia.programar(this);
            pedirPermisoNotificaciones();
        }
        if (prefs.getString("pass", "").isEmpty()) pedirContrasena(null);
        buscarActualizacion(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(ciclo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(ciclo);
    }

    // ---------- Interfaz ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable caja(int color, float radio, int borde) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radio));
        if (borde != 0) g.setStroke(dp(1), borde);
        return g;
    }

    private TextView texto(String s, float sp, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (negrita) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private LinearLayout.LayoutParams ancho(int arriba) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(arriba);
        return lp;
    }

    private TextView boton(String titulo, String sub, boolean principal, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(sub == null ? titulo : titulo + "\n" + sub);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setPadding(dp(20), dp(16), dp(20), dp(16));
        t.setLineSpacing(dp(2), 1f);
        if (principal) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{AZUL, VIOLETA});
            g.setCornerRadius(dp(18));
            t.setBackground(g);
        } else {
            t.setBackground(caja(TARJETA, 18, BORDE));
        }
        t.setOnClickListener(click);
        return t;
    }

    private View construir() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(FONDO);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(28), dp(22), dp(28));
        sv.addView(col);

        // Encabezado
        LinearLayout cab = new LinearLayout(this);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        cab.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout tit = new LinearLayout(this);
        tit.setOrientation(LinearLayout.VERTICAL);
        tit.setPadding(dp(14), 0, 0, 0);
        tit.addView(texto("LitleJacob Cloud", 24, TEXTO, true));
        tit.addView(texto("Tu Chrome en la nube", 14, GRIS, false));
        cab.addView(tit);
        col.addView(cab);

        // Estado del servidor
        LinearLayout est = new LinearLayout(this);
        est.setOrientation(LinearLayout.VERTICAL);
        est.setBackground(caja(TARJETA, 18, BORDE));
        est.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout fila = new LinearLayout(this);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        punto = new View(this);
        punto.setBackground(circulo(AMBAR));
        fila.addView(punto, new LinearLayout.LayoutParams(dp(12), dp(12)));
        estado = texto("Revisando servidor…", 17, TEXTO, true);
        estado.setPadding(dp(12), 0, 0, 0);
        fila.addView(estado);
        est.addView(fila);
        detalle = texto("Toca aquí para volver a revisar", 13, GRIS, false);
        detalle.setPadding(dp(24), dp(4), 0, 0);
        est.addView(detalle);
        est.setOnClickListener(v -> revisarServidor());
        col.addView(est, ancho(26));

        // Aviso de actualización (oculto hasta que haya una)
        bannerUpd = new LinearLayout(this);
        bannerUpd.setOrientation(LinearLayout.VERTICAL);
        bannerUpd.setBackground(caja(0xFF102A43, 18, CIAN));
        bannerUpd.setPadding(dp(18), dp(14), dp(18), dp(14));
        txtUpd = texto("Hay una actualización", 16, TEXTO, true);
        bannerUpd.addView(txtUpd);
        TextView btnUpd = texto("Actualizar ahora  →", 15, CIAN, true);
        btnUpd.setPadding(0, dp(8), 0, 0);
        bannerUpd.addView(btnUpd);
        bannerUpd.setOnClickListener(v -> confirmarActualizacion());
        bannerUpd.setVisibility(View.GONE);
        col.addView(bannerUpd, ancho(14));

        // Acciones principales
        col.addView(boton("👁  Ver pantalla", "Mira lo que hace Claude, sin tocar nada", true,
                v -> abrirVisor(false)), ancho(22));
        col.addView(boton("🖐  Controlar pantalla", "Toca y escribe, por ejemplo para iniciar sesión", false,
                v -> abrirVisor(true)), ancho(12));
        col.addView(boton("✳  Abrir Claude", "Ir a la app de Claude para darle órdenes", false,
                v -> abrirClaude()), ancho(12));

        // Ajustes
        col.addView(texto("AJUSTES", 12, GRIS, true), ancho(28));
        LinearLayout aj = new LinearLayout(this);
        aj.setOrientation(LinearLayout.VERTICAL);
        aj.setBackground(caja(TARJETA, 18, BORDE));
        aj.setPadding(dp(18), dp(6), dp(18), dp(6));

        Switch sw = new Switch(this);
        sw.setText("Avisarme si el servidor se cae");
        sw.setTextColor(TEXTO);
        sw.setTextSize(15);
        sw.setPadding(0, dp(12), 0, dp(12));
        sw.setChecked(prefs.getBoolean("aviso", true));
        sw.setOnCheckedChangeListener((x, on) -> {
            prefs.edit().putBoolean("aviso", on).apply();
            if (on) {
                Vigia.programar(this);
                pedirPermisoNotificaciones();
                Toast.makeText(this, "Te avisaré si el servidor deja de responder", Toast.LENGTH_SHORT).show();
            } else {
                Vigia.cancelar(this);
            }
        });
        aj.addView(sw);
        aj.addView(separador());
        TextView cp = texto("Cambiar contraseña de la pantalla", 15, TEXTO, false);
        cp.setPadding(0, dp(14), 0, dp(14));
        cp.setOnClickListener(v -> pedirContrasena(null));
        aj.addView(cp);
        aj.addView(separador());
        TextView bu = texto("Buscar actualización", 15, TEXTO, false);
        bu.setPadding(0, dp(14), 0, dp(14));
        bu.setOnClickListener(v -> buscarActualizacion(true));
        aj.addView(bu);
        col.addView(aj, ancho(10));

        TextView ver = texto("Versión " + nombreVersion(), 12, GRIS, false);
        ver.setGravity(Gravity.CENTER);
        col.addView(ver, ancho(24));
        return sv;
    }

    private View separador() {
        View v = new View(this);
        v.setBackgroundColor(BORDE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }

    private GradientDrawable circulo(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    // ---------- Acciones ----------

    private void abrirVisor(boolean controlar) {
        if (prefs.getString("pass", "").isEmpty()) {
            pedirContrasena(() -> abrirVisor(controlar));
            return;
        }
        startActivity(new Intent(this, VisorActivity.class).putExtra("controlar", controlar));
    }

    private void abrirClaude() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.anthropic.claude");
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai"));
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir Claude", Toast.LENGTH_SHORT).show();
        }
    }

    private void pedirContrasena(Runnable despues) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        et.setText(prefs.getString("pass", ""));
        et.setHint("Contraseña");
        et.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Contraseña de la pantalla")
                .setMessage("Escríbela una sola vez. Se guarda solo en este celular y la app la pondrá sola cada vez.")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    prefs.edit().putString("pass", et.getText().toString().trim()).apply();
                    Toast.makeText(this, "Contraseña guardada", Toast.LENGTH_SHORT).show();
                    if (despues != null) despues.run();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    private void revisarServidor() {
        estado.setText("Revisando servidor…");
        punto.setBackground(circulo(AMBAR));
        new Thread(() -> {
            int code = Red.estado(Red.PANTALLA);
            runOnUiThread(() -> {
                if (code == 200) {
                    punto.setBackground(circulo(VERDE));
                    estado.setText("Servidor en línea");
                    detalle.setText("Todo funcionando · toca para revisar de nuevo");
                } else if (code == -1) {
                    punto.setBackground(circulo(AMBAR));
                    estado.setText("Sin internet en el celular");
                    detalle.setText("Revisa tu conexión y toca para reintentar");
                } else {
                    punto.setBackground(circulo(ROJO));
                    estado.setText("Servidor sin respuesta");
                    detalle.setText("Código " + code + " · puede estar apagado o reiniciándose");
                }
            });
        }).start();
    }

    // ---------- Actualizaciones ----------

    private int codigoVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String nombreVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void buscarActualizacion(boolean manual) {
        if (manual) Toast.makeText(this, "Buscando actualización…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String t = Red.texto(Red.VERSION_URL + "?t=" + System.currentTimeMillis());
            int remota = 0;
            try { if (t != null) remota = Integer.parseInt(t.trim()); } catch (Exception ignored) { }
            final int r = remota;
            runOnUiThread(() -> {
                if (r > codigoVersion()) {
                    versionRemota = r;
                    txtUpd.setText("Hay una actualización disponible (versión 1." + r + ")");
                    bannerUpd.setVisibility(View.VISIBLE);
                    if (manual) confirmarActualizacion();
                } else if (manual) {
                    Toast.makeText(this, r == 0 ? "No se pudo revisar ahora" : "Ya tienes la última versión",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void confirmarActualizacion() {
        new AlertDialog.Builder(this)
                .setTitle("Actualizar LitleJacob Cloud")
                .setMessage("Se descargará la versión 1." + versionRemota + ". Luego Android te pedirá tocar \"Actualizar\" o \"Instalar\".")
                .setPositiveButton("Actualizar", (d, w) -> descargarEInstalar())
                .setNegativeButton("Después", null)
                .show();
    }

    private void descargarEInstalar() {
        AlertDialog progreso = new AlertDialog.Builder(this)
                .setTitle("Descargando actualización…")
                .setMessage("Un momento, no cierres la app.")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            PackageInstaller.Session sesion = null;
            try {
                PackageInstaller pi = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = pi.createSession(params);
                sesion = pi.openSession(id);
                HttpURLConnection c = (HttpURLConnection) new URL(Red.APK_URL).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) throw new Exception("descarga " + c.getResponseCode());
                InputStream in = c.getInputStream();
                OutputStream out = sesion.openWrite("app.apk", 0, -1);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                sesion.fsync(out);
                out.close();
                in.close();
                c.disconnect();
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent p = PendingIntent.getBroadcast(this, id,
                        new Intent(this, InstallReceiver.class), flags);
                sesion.commit(p.getIntentSender());
                sesion.close();
                runOnUiThread(progreso::dismiss);
            } catch (Exception e) {
                if (sesion != null) sesion.abandon();
                runOnUiThread(() -> {
                    progreso.dismiss();
                    Toast.makeText(this, "No se pudo descargar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
