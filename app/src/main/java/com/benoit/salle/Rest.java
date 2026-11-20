package com.benoit.salle;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Rest {
    static final int ID_COUNTDOWN = 8;
    static final int ID_DONE = 7;
    static volatile long end = 0;

    private Rest() {}

    private static PendingIntent alarm(Context c) {
        Intent i = new Intent(c, TimerReceiver.class);
        i.setAction("com.benoit.salle.REPOS");
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent open(Context c) {
        Intent o = new Intent(c, MainActivity.class);
        o.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, o, PendingIntent.FLAG_IMMUTABLE);
    }

    static void start(Context c, int seconds) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarm(c));
        if (seconds <= 0) { stop(c); return; }
        end = System.currentTimeMillis() + seconds * 1000L;
        // alarme exacte (type réveil) : sonne à l'heure même écran éteint ; sinon repli sur l'alarme normale
        boolean exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setAlarmClock(new AlarmManager.AlarmClockInfo(end, open(c)), alarm(c));
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(c));
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(c));
        }
        if (!MainActivity.visible) showCountdown(c);
    }

    static void stop(Context c) {
        end = 0;
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));
        hideCountdown(c);
    }

    static void showCountdown(Context c) {
        if (end <= System.currentTimeMillis()) return;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            // importance normale mais silencieuse : visible sur l'écran verrouillé (les canaux « faibles » y sont souvent masqués)
            NotificationChannel ch = new NotificationChannel("compte2", "Compte à rebours du repos", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(c, "compte2")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Repos en cours")
                    .setContentText("Prochaine série à la fin du compte à rebours")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(end)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(open(c))
                    .build();
            nm.notify(ID_COUNTDOWN, n);
        } catch (Exception e) {
            // notifications refusées
        }
    }

    static void hideCountdown(Context c) {
        try {
            c.getSystemService(NotificationManager.class).cancel(ID_COUNTDOWN);
        } catch (Exception e) {
            // rien à faire
        }
    }

    static void done(Context c) {
        end = 0;
        hideCountdown(c);
        if (MainActivity.visible) return;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            // le bip et la vibration sont joués par TimerReceiver : notification sans son pour ne pas doubler
            NotificationChannel ch = new NotificationChannel("repos2", "Fin du repos", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(c, "repos2")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Repos terminé")
                    .setContentText("Série suivante !")
                    .setContentIntent(open(c))
                    .setAutoCancel(true)
                    .build();
            nm.notify(ID_DONE, n);
        } catch (Exception e) {
            // notifications refusées
        }
    }
}
