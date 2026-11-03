package trade.litlejacob.cloud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Notifs {
    static final String CANAL = "servidor";

    static void crearCanal(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Estado del servidor", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisa cuando tu servidor se cae o vuelve a estar en línea");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    static void mostrar(Context c, String titulo, String mensaje) {
        crearCanal(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CANAL)
                : new Notification.Builder(c);
        PendingIntent abrir = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new Notification.BigTextStyle().bigText(mensaje))
                .setAutoCancel(true)
                .setContentIntent(abrir);
        try {
            c.getSystemService(NotificationManager.class).notify(2, b.build());
        } catch (SecurityException ignored) {
            // Sin permiso de notificaciones: no se muestra.
        }
    }
}
