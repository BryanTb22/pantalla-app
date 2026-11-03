package trade.litlejacob.cloud;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Utilidades de red: estado del servidor y lectura de textos cortos. */
public class Red {
    public static final String PANTALLA = "https://pantalla.litlejacob.trade/vnc.html";
    public static final String VERSION_URL = "https://github.com/BryanTb22/pantalla-app/releases/latest/download/version.txt";
    public static final String APK_URL = "https://github.com/BryanTb22/pantalla-app/releases/latest/download/LitleJacobCloud.apk";

    /** Devuelve el código HTTP, o -1 si el celular no tiene conexión. */
    public static int estado(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            return c.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String texto(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close();
            return out.toString("UTF-8").trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
