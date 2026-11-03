package trade.litlejacob.cloud;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.net.URLEncoder;

/** Muestra la pantalla del servidor a pantalla completa, en modo ver o controlar. */
public class VisorActivity extends Activity {
    private WebView w;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean controlar = getIntent().getBooleanExtra("controlar", false);
        String pass = getSharedPreferences("cfg", MODE_PRIVATE).getString("pass", "");
        // La pantalla del celular no se apaga mientras miras.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.BLACK);

        w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        w.setWebViewClient(new WebViewClient());
        w.setBackgroundColor(Color.BLACK);
        raiz.addView(w, new FrameLayout.LayoutParams(-1, -1));

        // Botón pequeño para volver al panel.
        TextView cerrar = new TextView(this);
        cerrar.setText("✕");
        cerrar.setTextColor(Color.WHITE);
        cerrar.setTextSize(16);
        cerrar.setGravity(Gravity.CENTER);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setShape(GradientDrawable.OVAL);
        fondo.setColor(0x66000000);
        cerrar.setBackground(fondo);
        int t = dp(34);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(t, t, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(8), dp(8), 0);
        cerrar.setOnClickListener(v -> finish());
        raiz.addView(cerrar, lp);

        setContentView(raiz);
        ocultarBarras();

        String url = Red.PANTALLA + "?autoconnect=true&resize=scale&reconnect=true&reconnect_delay=3000"
                + "&view_only=" + (controlar ? "false" : "true");
        try {
            if (!pass.isEmpty()) url += "&password=" + URLEncoder.encode(pass, "UTF-8");
        } catch (Exception ignored) { }
        if (b != null) w.restoreState(b); else w.loadUrl(url);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void ocultarBarras() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) ocultarBarras();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        w.saveState(o);
    }

    @Override
    protected void onDestroy() {
        if (w != null) w.destroy();
        super.onDestroy();
    }
}
