package trade.litlejacob.cloud;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Utilidades de red: estado del servidor, panel y lectura de textos cortos. */
public class Red {
    public static final String PANTALLA = "https://pantalla.litlejacob.trade/vnc.html";
    public static final String PANEL = "https://mcp.litlejacob.trade/panel/";
    public static final String VERSION_URL = "https://github.com/BryanTb22/pantalla-app/releases/latest/download/version.txt";
    public static final String APK_URL = "https://github.com/BryanTb22/pantalla-app/releases/latest/download/LitleJacobCloud.apk";

    /** Respuesta simple: código HTTP (-1 = sin conexión) y texto. */
    public static class Respuesta {
        public final int codigo;
        public final String cuerpo;
        Respuesta(int c, String b) { codigo = c; cuerpo = b; }
    }

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

    /** Llama al agente del panel del servidor con la clave guardada. */
    public static Respuesta panel(String metodo, String ruta, String clave) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(PANEL + ruta).openConnection();
            c.setRequestMethod(metodo);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setUseCaches(false);
            c.setRequestProperty("X-Clave", clave == null ? "" : clave);
            if ("POST".equals(metodo)) {
                c.setDoOutput(true);
                c.getOutputStream().write(new byte[0]);
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            return new Respuesta(code, leer(in));
        } catch (Exception e) {
            return new Respuesta(-1, null);
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
            return leer(c.getInputStream()).trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String leer(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}
