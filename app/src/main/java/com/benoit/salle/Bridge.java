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
