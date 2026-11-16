package com.benoit.salle;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

public class Bridge {
    private final Activity act;

    Bridge(Activity act) {
        this.act = act;
    }

    private PendingIntent pending() {
        Intent i = new Intent(act, TimerReceiver.class);
        i.setAction("com.benoit.salle.REPOS");
        return PendingIntent.getBroadcast(act, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @JavascriptInterface
    public void timer(int seconds) {
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending());
        if (seconds > 0) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000L, pending());
        }
    }

    @JavascriptInterface
    public void cancelTimer() {
        AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending());
    }

    @JavascriptInterface
    public void keepAwake(final boolean on) {
        act.runOnUiThread(() -> {
            if (on) act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }
}
