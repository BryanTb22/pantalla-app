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
import android.text.TextUtils;
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
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/** Panel de inicio de LitleJacob Cloud. */
public class MainActivity extends Activity {
    static final int FONDO = 0xFF0B1020, TARJETA = 0xFF151B2E, BORDE = 0xFF252D45;
    static final int AZUL = 0xFF2563EB, VIOLETA = 0xFF7C3AED, CIAN = 0xFF22D3EE;
    static final int TEXTO = 0xFFF1F5F9, GRIS = 0xFF94A3B8, VERDE = 0xFF22C55E, ROJO = 0xFFEF4444, AMBAR = 0xFFF59E0B;

    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private View punto;
    private TextView estado, detalle, txtUpd, indPantalla, indConector, indChrome, recursos, tituloPestanas;
    private LinearLayout listaPestanas, listaSesiones, listaEventos;
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
        LinearLayout ind = new LinearLayout(this);
        ind.setPadding(dp(24), dp(10), 0, 0);
        indPantalla = indicador(ind);
        indConector = indicador(ind);
        indChrome = indicador(ind);
        ponerIndicador(indPantalla, "Pantalla", null);
        ponerIndicador(indConector, "Conector", null);
        ponerIndicador(indChrome, "Chrome", null);
        est.addView(ind);
        recursos = texto("", 13, GRIS, false);
        recursos.setPadding(dp(24), dp(8), 0, 0);
        recursos.setVisibility(View.GONE);
        est.addView(recursos);
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

        // Pestañas abiertas en el Chrome nube
        tituloPestanas = texto("PESTAÑAS ABIERTAS", 12, GRIS, true);
        col.addView(tituloPestanas, ancho(28));
        listaPestanas = tarjeta();
        listaPestanas.addView(nota("Cargando…"));
        col.addView(listaPestanas, ancho(10));

        // Sesiones iniciadas
        col.addView(texto("SESIONES EN EL CHROME NUBE", 12, GRIS, true), ancho(28));
        listaSesiones = tarjeta();
        listaSesiones.addView(nota("Cargando…"));
        col.addView(listaSesiones, ancho(10));

        // Herramientas
        col.addView(texto("HERRAMIENTAS", 12, GRIS, true), ancho(28));
        LinearLayout her = tarjeta();
        her.addView(opcion("🔄  Reiniciar Chrome", "Lo cierra y lo vuelve a abrir con las mismas pestañas",
                v -> accionServidor("reiniciar-chrome", "¿Reiniciar Chrome?",
                        "Chrome se cerrará unos segundos y volverá con tus pestañas. Si Claude está trabajando, se interrumpe.",
                        "Reiniciando Chrome…")));
        her.addView(separador());
        her.addView(opcion("🧹  Limpiar caché", "Libera espacio y memoria (Chrome se reinicia ~20 s)",
                v -> accionServidor("limpiar", "¿Limpiar caché?",
                        "Se borra la caché y las descargas de más de 14 días. Tus sesiones NO se cierran. Chrome se reinicia unos segundos.",
                        "Limpiando…")));
        col.addView(her, ancho(10));

        // Últimos eventos
        col.addView(texto("ÚLTIMOS EVENTOS DEL SERVIDOR", 12, GRIS, true), ancho(28));
        listaEventos = tarjeta();
        listaEventos.addView(nota("Cargando…"));
        col.addView(listaEventos, ancho(10));

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

    private LinearLayout tarjeta() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(caja(TARJETA, 18, BORDE));
        t.setPadding(dp(18), dp(6), dp(18), dp(6));
        return t;
    }

    private TextView nota(String s) {
        TextView t = texto(s, 14, GRIS, false);
        t.setPadding(0, dp(12), 0, dp(12));
        return t;
    }

    private View opcion(String titulo, String sub, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(12), 0, dp(12));
        l.addView(texto(titulo, 15, TEXTO, true));
        TextView s = texto(sub, 13, GRIS, false);
        s.setPadding(0, dp(2), 0, 0);
        l.addView(s);
        l.setOnClickListener(click);
        return l;
    }

    private TextView indicador(LinearLayout fila) {
        TextView t = texto("", 13, GRIS, true);
        t.setPadding(0, 0, dp(16), 0);
        fila.addView(t);
        return t;
    }

    private void ponerIndicador(TextView t, String nombre, Boolean ok) {
        t.setText("● " + nombre);
        t.setTextColor(ok == null ? GRIS : (ok ? VERDE : ROJO));
    }

    private static String gb(double bytes) {
        return String.format(new Locale("es"), "%.1f", bytes / 1073741824.0);
    }

    private static String duracion(long seg) {
        long d = seg / 86400, h = (seg % 86400) / 3600, m = (seg % 3600) / 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m + " min";
        return m + " min";
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
                .setMessage("Escríbela una sola vez. Se guarda solo en este celular y la app la pondrá sola cada vez. Es la misma clave para la pantalla y el panel.")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    prefs.edit().putString("pass", et.getText().toString().trim()).apply();
                    Toast.makeText(this, "Contraseña guardada", Toast.LENGTH_SHORT).show();
                    revisarServidor();
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
        final String clave = prefs.getString("pass", "");
        new Thread(() -> {
            Red.Respuesta r = Red.panel("GET", "estado", clave);
            JSONObject j = null;
            if (r.codigo == 200) {
                try { j = new JSONObject(r.cuerpo); } catch (Exception ignored) { }
            }
            final int codPantalla = (j == null) ? Red.estado(Red.PANTALLA) : 0;
            final JSONObject fj = j;
            final int codPanel = r.codigo;
            runOnUiThread(() -> pintarEstado(fj, codPanel, codPantalla));
        }).start();
    }

    private void pintarEstado(JSONObject j, int codPanel, int codPantalla) {
        if (j != null) {
            boolean pant = j.optBoolean("pantalla"), con = j.optBoolean("conector"), chr = j.optBoolean("chrome");
            ponerIndicador(indPantalla, "Pantalla", pant);
            ponerIndicador(indConector, "Conector", con);
            ponerIndicador(indChrome, "Chrome", chr);
            boolean todo = pant && con && chr;
            punto.setBackground(circulo(todo ? VERDE : ROJO));
            estado.setText(todo ? "Todo funcionando" : "Hay un problema");
            if (todo) detalle.setText("Toca aquí para revisar de nuevo");
            else if (!chr) detalle.setText("Chrome no responde. El vigilante lo reinicia solo; si sigue así, usa Reiniciar Chrome.");
            else if (!con) detalle.setText("El conector no responde: Claude no puede usar tu Chrome. Prueba Reiniciar Chrome.");
            else detalle.setText("La pantalla remota no responde. El vigilante la reinicia sola en unos minutos.");
            JSONObject s = j.optJSONObject("sistema");
            if (s != null) {
                JSONObject ram = s.optJSONObject("ram"), disco = s.optJSONObject("disco");
                StringBuilder b = new StringBuilder();
                if (ram != null) b.append("RAM ").append(gb(ram.optDouble("usada"))).append(" / ").append(gb(ram.optDouble("total"))).append(" GB");
                if (disco != null) b.append("  ·  Disco ").append(gb(disco.optDouble("usado"))).append(" / ").append(gb(disco.optDouble("total"))).append(" GB");
                b.append("\n").append(s.optInt("nucleos")).append(" núcleos  ·  encendido hace ").append(duracion(s.optLong("encendidoSeg")));
                recursos.setText(b.toString());
                recursos.setVisibility(View.VISIBLE);
            }
            pintarPestanas(j.optJSONArray("pestanas"));
            pintarSesiones(j.optJSONArray("sesiones"));
            pintarEventos(j.optJSONArray("eventos"));
            return;
        }
        ponerIndicador(indConector, "Conector", null);
        ponerIndicador(indChrome, "Chrome", null);
        recursos.setVisibility(View.GONE);
        if (codPanel == 401) {
            punto.setBackground(circulo(AMBAR));
            estado.setText("Clave incorrecta");
            detalle.setText("Actualiza la contraseña en Ajustes para ver el panel completo");
            ponerIndicador(indPantalla, "Pantalla", null);
        } else if (codPantalla == 200) {
            punto.setBackground(circulo(AMBAR));
            estado.setText("Pantalla en línea");
            detalle.setText("No se pudo leer el resto del servidor (código " + codPanel + ")");
            ponerIndicador(indPantalla, "Pantalla", true);
        } else if (codPantalla == -1) {
            punto.setBackground(circulo(AMBAR));
            estado.setText("Sin internet en el celular");
            detalle.setText("Revisa tu conexión y toca para reintentar");
            ponerIndicador(indPantalla, "Pantalla", null);
        } else {
            punto.setBackground(circulo(ROJO));
            estado.setText("Servidor sin respuesta");
            detalle.setText("Código " + codPantalla + " · puede estar apagado o reiniciándose");
            ponerIndicador(indPantalla, "Pantalla", false);
        }
        String sin = codPanel == 401 ? "Necesita la clave correcta" : "Sin datos del servidor";
        listaPestanas.removeAllViews(); listaPestanas.addView(nota(sin));
        listaSesiones.removeAllViews(); listaSesiones.addView(nota(sin));
        listaEventos.removeAllViews(); listaEventos.addView(nota(sin));
    }

    private void pintarPestanas(JSONArray a) {
        listaPestanas.removeAllViews();
        if (a == null) { listaPestanas.addView(nota("No se pudo leer Chrome")); return; }
        tituloPestanas.setText("PESTAÑAS ABIERTAS (" + a.length() + ")");
        if (a.length() == 0) { listaPestanas.addView(nota("Ninguna pestaña abierta")); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t == null) continue;
            if (i > 0) listaPestanas.addView(separador());
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.VERTICAL);
            fila.setPadding(0, dp(11), 0, dp(11));
            TextView tt = texto(t.optString("titulo"), 15, TEXTO, false);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            fila.addView(tt);
            String host = "";
            try { host = Uri.parse(t.optString("url")).getHost(); } catch (Exception ignored) { }
            TextView th = texto(host == null || host.isEmpty() ? t.optString("url") : host, 12, GRIS, false);
            th.setSingleLine(true);
            th.setEllipsize(TextUtils.TruncateAt.END);
            fila.addView(th);
            fila.setOnClickListener(v -> abrirVisor(false));
            listaPestanas.addView(fila);
        }
    }

    private void pintarSesiones(JSONArray a) {
        listaSesiones.removeAllViews();
        if (a == null) { listaSesiones.addView(nota("No se pudo leer Chrome")); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            if (i > 0) listaSesiones.addView(separador());
            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(0, dp(11), 0, dp(11));
            fila.addView(texto(s.optString("nombre"), 15, TEXTO, false), new LinearLayout.LayoutParams(0, -2, 1f));
            boolean act = s.optBoolean("activa");
            fila.addView(texto(act ? "● Activa" : "● Cerrada", 14, act ? VERDE : ROJO, true));
            if (!act) fila.setOnClickListener(v -> abrirVisor(true));
            listaSesiones.addView(fila);
        }
        TextView n = texto("Si una sesión está cerrada, tócala para entrar con Controlar pantalla.", 12, GRIS, false);
        n.setPadding(0, dp(4), 0, dp(10));
        listaSesiones.addView(n);
    }

    private void pintarEventos(JSONArray a) {
        listaEventos.removeAllViews();
        if (a == null || a.length() == 0) { listaEventos.addView(nota("Sin novedades: todo estable.")); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < Math.min(6, a.length()); i++) {
            TextView t = texto(a.optString(i), 12, GRIS, false);
            t.setPadding(0, dp(3), 0, dp(3));
            box.addView(t);
        }
        listaEventos.addView(box);
    }

    private void accionServidor(String ruta, String titulo, String mensaje, String hecho) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Sí", (d, w) -> {
                    final String clave = prefs.getString("pass", "");
                    Toast.makeText(this, hecho, Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        Red.Respuesta r = Red.panel("POST", ruta, clave);
                        runOnUiThread(() -> {
                            if (r.codigo == 200) {
                                Toast.makeText(this, "Listo. En unos segundos vuelve a estar disponible.", Toast.LENGTH_LONG).show();
                                h.postDelayed(this::revisarServidor, 20000);
                            } else if (r.codigo == 401) {
                                Toast.makeText(this, "Clave incorrecta: actualízala en Ajustes", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "No se pudo (código " + r.codigo + ")", Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
