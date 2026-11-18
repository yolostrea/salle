package com.benoit.salle;

import android.app.Activity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

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
}
