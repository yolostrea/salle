package com.benoit.salle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class TimerReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "repos";

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } catch (Exception e) {
            // pas de vibreur
        }
        if (MainActivity.visible) return;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Fin du repos", NotificationManager.IMPORTANCE_HIGH));
            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Repos terminé")
                    .setContentText("Série suivante !")
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build();
            nm.notify(7, n);
        } catch (Exception e) {
            // notifications refusées
        }
    }
}
