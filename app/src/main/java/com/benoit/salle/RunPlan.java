package com.benoit.salle;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

/** Course guidée : une alarme exacte par changement de phase, avec bip + vibration + notification, même écran éteint. */
final class RunPlan {
    static final String ACT = "com.benoit.salle.PHASE";
    private static final int BASE = 100, MAX = 120, NID = 9;

    private RunPlan() {}

    private static PendingIntent pi(Context c, int i, String lab, long next, boolean end) {
        Intent x = new Intent(c, TimerReceiver.class);
        x.setAction(ACT);
        x.putExtra("lab", lab);
        x.putExtra("next", String.valueOf(next));
        x.putExtra("end", end ? 1 : 0);
        return PendingIntent.getBroadcast(c, BASE + i, x, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent open(Context c) {
        Intent o = new Intent(c, MainActivity.class);
        o.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 2, o, PendingIntent.FLAG_IMMUTABLE);
    }

    static void plan(Context c, String json) {
        stop(c);
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            boolean exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length() && i < MAX; i++) {
                JSONObject o = a.getJSONObject(i);
                long at = o.getLong("at");
                PendingIntent p = pi(c, i, o.optString("lab"), o.getLong("next"), o.optBoolean("end"));
                try {
                    if (exact) am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, open(c)), p);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                } catch (Exception e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                }
            }
        } catch (Exception e) {
            // plan illisible : la page garde son propre chrono
        }
    }

    static void stop(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < MAX; i++) am.cancel(pi(c, i, "", 0, false));
            c.getSystemService(NotificationManager.class).cancel(NID);
        } catch (Exception e) {
            // rien à annuler
        }
    }

    static void phase(Context c, Intent i) {
        boolean end = i.getIntExtra("end", 0) == 1;
        String lab = i.getStringExtra("lab");
        long next = 0;
        try { next = Long.parseLong(i.getStringExtra("next")); } catch (Exception e) { next = 0; }
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(end ? new long[]{0, 500, 200, 500, 200, 500} : new long[]{0, 250, 150, 250}, -1));
        } catch (Exception e) {
            // pas de vibreur
        }
        try {
            final android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 90);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, end ? 1500 : 500);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tg::release, 2000);
        } catch (Exception e) {
            // pas de son
        }
        if (MainActivity.visible && !end) return;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel("course", "Course guidée", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            Notification.Builder b = new Notification.Builder(c, "course")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(end ? "Course terminée" : "Course guidée")
                    .setContentText(lab == null ? "" : lab)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setContentIntent(open(c))
                    .setOnlyAlertOnce(true);
            if (end) b.setAutoCancel(true);
            else {
                b.setOngoing(true);
                if (next > System.currentTimeMillis()) b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(next).setShowWhen(true);
            }
            nm.notify(NID, b.build());
        } catch (Exception e) {
            // notifications refusées
        }
    }
}
