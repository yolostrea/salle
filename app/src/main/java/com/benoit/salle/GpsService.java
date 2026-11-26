package com.benoit.salle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;

/** Suivi GPS d'une course en extérieur, en service de premier plan : continue écran éteint. */
public class GpsService extends Service implements LocationListener {
    static final int NID = 11;
    // état partagé avec la page (lu via Bridge)
    static volatile boolean on = false, paused = false, kmBeep = true;
    static volatile double dist = 0, acc = -1;
    static volatile long t0 = 0, pauseAt = 0, pausedTot = 0, lastFix = 0;
    static final ArrayList<double[]> pts = new ArrayList<>();      // lat, lon
    static final ArrayList<Long> splits = new ArrayList<>();       // temps de course (ms) à chaque km
    static final ArrayList<double[]> recent = new ArrayList<>();   // temps (ms), distance cumulée : vitesse du moment
    private static Location last = null;
    private LocationManager lm;

    static synchronized void reset() {
        dist = 0; acc = -1; pts.clear(); splits.clear(); recent.clear(); last = null;
        t0 = SystemClock.elapsedRealtime(); pauseAt = 0; pausedTot = 0; paused = false; lastFix = 0;
    }

    static long runMs() {
        long now = SystemClock.elapsedRealtime();
        return now - t0 - pausedTot - (paused ? now - pauseAt : 0);
    }

    static synchronized void setPaused(boolean p) {
        if (p == paused) return;
        if (p) { pauseAt = SystemClock.elapsedRealtime(); paused = true; last = null; }
        else { pausedTot += SystemClock.elapsedRealtime() - pauseAt; paused = false; last = null; }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NID, note("Recherche du signal GPS…"), 8 /* ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION */);
        else startForeground(NID, note("Recherche du signal GPS…"));
        try {
            lm = getSystemService(LocationManager.class);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception e) { /* rien */ }
        on = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onLocationChanged(Location l) {
        synchronized (GpsService.class) {
            acc = l.getAccuracy();
            lastFix = System.currentTimeMillis();
            if (paused || !l.hasAccuracy() || l.getAccuracy() > 25f) return;
            if (last == null) { last = l; pts.add(new double[]{l.getLatitude(), l.getLongitude()}); return; }
            float d = last.distanceTo(l);
            double dt = Math.max(0.5, (l.getElapsedRealtimeNanos() - last.getElapsedRealtimeNanos()) / 1e9);
            if (d / dt > 8.5) return;                                   // saut impossible (> 30 km/h)
            if (d < Math.max(3f, l.getAccuracy() * 0.5f)) return;       // bruit sur place
            double before = dist;
            dist += d;
            last = l;
            pts.add(new double[]{l.getLatitude(), l.getLongitude()});
            long ms = runMs();
            recent.add(new double[]{ms, dist});
            while (recent.size() > 1 && ms - recent.get(0)[0] > 20000) recent.remove(0);
            if (Math.floor(dist / 1000) > Math.floor(before / 1000)) {
                splits.add(ms);
                long prev = splits.size() > 1 ? splits.get(splits.size() - 2) : 0;
                String km = splits.size() + " km · " + mmss(ms - prev);
                getSystemService(NotificationManager.class).notify(NID, note(km));
                if (kmBeep) beep();
            }
        }
    }

    static String mmss(long ms) {
        long s = Math.round(ms / 1000.0);
        return (s / 60) + "'" + (s % 60 < 10 ? "0" : "") + (s % 60) + "\"";
    }

    private void beep() {
        try {
            android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 200, 120, 200, 120, 200}, -1));
        } catch (Exception e) { /* pas de vibreur */ }
        try {
            final android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 80);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 600);
            new android.os.Handler(Looper.getMainLooper()).postDelayed(tg::release, 1200);
        } catch (Exception e) { /* pas de son */ }
    }

    private Notification note(String txt) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel("gps", "Course en extérieur", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        Intent o = new Intent(this, MainActivity.class);
        o.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new Notification.Builder(this, "gps")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Course en cours")
                .setContentText(txt)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 3, o, PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    // anciennes versions d'Android
    @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
}
