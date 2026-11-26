package com.benoit.salle;

import android.app.Activity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Bridge {
    private final Activity act;

    Bridge(Activity act) {
        this.act = act;
    }

    /** Copie de secours interne des données (en plus du stockage de la page). Écriture atomique. */
    @JavascriptInterface
    public void mirror(String json) {
        try {
            File dir = act.getFilesDir();
            File tmp = new File(dir, "merlin.tmp"), dst = new File(dir, "merlin.json");
            FileOutputStream o = new FileOutputStream(tmp);
            o.write(json.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
            o.close();
            if (!tmp.renameTo(dst)) {
                dst.delete();
                tmp.renameTo(dst);
            }
        } catch (Exception e) {
            // la copie échoue : le stockage de la page reste la référence
        }
    }

    @JavascriptInterface
    public String mirrorGet() {
        try {
            File f = new File(act.getFilesDir(), "merlin.json");
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** Notifications autorisées ? (sinon pas de compte à rebours écran éteint) */
    @JavascriptInterface
    public boolean notifOk() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            return act.getSystemService(android.app.NotificationManager.class).areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    /** Redemande l'autorisation, ou ouvre les réglages de notifications de l'appli si elle a été refusée. */
    @JavascriptInterface
    public void askNotif() {
        act.runOnUiThread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 33 && act.shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                    act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                } else {
                    android.content.Intent i = new android.content.Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                    i.putExtra("android.provider.extra.APP_PACKAGE", act.getPackageName());
                    act.startActivity(i);
                }
            } catch (Exception e) {
                // rien
            }
        });
    }

    @JavascriptInterface
    public void timer(int seconds) {
        Rest.start(act, seconds);
    }

    @JavascriptInterface
    public void cancelTimer() {
        Rest.stop(act);
    }

    @JavascriptInterface
    public void bars(final String status, final String nav) {
        act.getSharedPreferences("ui", 0).edit().putString("st", status).putString("nv", nav).apply();
        act.runOnUiThread(() -> MainActivity.applyBars(act, status, nav));
    }

    @JavascriptInterface
    public String version() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    @JavascriptInterface
    public void keepAwake(final boolean on) {
        act.runOnUiThread(() -> {
            if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    /** GPS : démarre le suivi. Renvoie "ok", ou "perm" si l'autorisation de localisation manque (elle est alors demandée). */
    @JavascriptInterface
    public String gpsStart(boolean kmBeep) {
        if (act.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            act.runOnUiThread(() -> act.requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"}, 7));
            return "perm";
        }
        GpsService.reset();
        GpsService.kmBeep = kmBeep;
        GpsService.on = true;
        try {
            act.startForegroundService(new android.content.Intent(act, GpsService.class));
        } catch (Exception e) {
            GpsService.on = false;
            return "err";
        }
        return "ok";
    }

    @JavascriptInterface
    public void gpsPause(boolean p) {
        GpsService.setPaused(p);
    }

    /** État du moment : {on, dist (m), acc (m), fix (ms depuis le dernier point), v (m/s sur ~20 s), km, ms} */
    @JavascriptInterface
    public String gpsState() {
        synchronized (GpsService.class) {
            double v = 0;
            if (GpsService.recent.size() > 1) {
                double[] a = GpsService.recent.get(0), b = GpsService.recent.get(GpsService.recent.size() - 1);
                if (b[0] > a[0]) v = (b[1] - a[1]) / ((b[0] - a[0]) / 1000.0);
            }
            long fix = GpsService.lastFix == 0 ? -1 : System.currentTimeMillis() - GpsService.lastFix;
            return "{\"on\":" + GpsService.on + ",\"dist\":" + Math.round(GpsService.dist) + ",\"acc\":" + Math.round(GpsService.acc)
                    + ",\"fix\":" + fix + ",\"v\":" + Math.round(v * 100) / 100.0 + ",\"km\":" + GpsService.splits.size() + ",\"ms\":" + GpsService.runMs() + "}";
        }
    }

    /** Arrête le suivi et renvoie le parcours : {dist, splits:[ms], pts:[[lat,lon]]} (au plus ~300 points). */
    @JavascriptInterface
    public String gpsStop() {
        StringBuilder sb = new StringBuilder();
        synchronized (GpsService.class) {
            sb.append("{\"dist\":").append(Math.round(GpsService.dist)).append(",\"splits\":[");
            for (int i = 0; i < GpsService.splits.size(); i++) sb.append(i > 0 ? "," : "").append(GpsService.splits.get(i));
            sb.append("],\"pts\":[");
            int n = GpsService.pts.size(), step = Math.max(1, n / 300);
            boolean first = true;
            for (int i = 0; i < n; i += step) {
                double[] q = GpsService.pts.get(i);
                sb.append(first ? "" : ",").append("[").append(Math.round(q[0] * 1e5) / 1e5).append(",").append(Math.round(q[1] * 1e5) / 1e5).append("]");
                first = false;
            }
            if (n > 1 && (n - 1) % step != 0) {
                double[] q = GpsService.pts.get(n - 1);
                sb.append(",[").append(Math.round(q[0] * 1e5) / 1e5).append(",").append(Math.round(q[1] * 1e5) / 1e5).append("]");
            }
            sb.append("]}");
            GpsService.on = false;
        }
        try { act.stopService(new android.content.Intent(act, GpsService.class)); } catch (Exception e) { /* déjà arrêté */ }
        return sb.toString();
    }

    /** Course guidée : programme les changements de phase (JSON [{at,lab,next,end}]). */
    @JavascriptInterface
    public void runPlan(String json) {
        RunPlan.plan(act, json);
    }

    @JavascriptInterface
    public void runStop() {
        RunPlan.stop(act);
    }

    @JavascriptInterface
    public void vibrate(int ms) {
        try {
            android.os.Vibrator v = act.getSystemService(android.os.Vibrator.class);
            if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(Math.max(30, Math.min(ms, 2000)), -1));
        } catch (Exception e) {
            // pas de vibreur
        }
    }

    @JavascriptInterface
    public String backup(String json, String name) {
        try {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = act.getContentResolver();
                Uri col = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri target = null;
                Cursor c = cr.query(col, new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{name, "Download/Merlin/"}, null);
                if (c != null) {
                    if (c.moveToFirst()) target = ContentUris.withAppendedId(col, c.getLong(0));
                    c.close();
                }
                if (target == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Merlin");
                    target = cr.insert(col, v);
                }
                if (target == null) return "";
                OutputStream o = cr.openOutputStream(target, "wt");
                if (o == null) return "";
                o.write(data);
                o.close();
                return "Téléchargements/Merlin/" + name;
            }
            File d = new File(act.getExternalFilesDir(null), "Merlin");
            if (!d.exists() && !d.mkdirs()) return "";
            FileOutputStream f = new FileOutputStream(new File(d, name));
            f.write(data);
            f.close();
            return "Android/data/com.benoit.salle/files/Merlin/" + name;
        } catch (Exception e) {
            return "";
        }
    }
}
