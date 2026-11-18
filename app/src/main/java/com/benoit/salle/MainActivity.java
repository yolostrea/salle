package com.benoit.salle;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static volatile boolean visible = false;
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int bar = Color.parseColor("#7a1d2a");
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(Color.parseColor("#f7f3f3"));
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#f7f3f3"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith("http")) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, req.getUrl()));
                    } catch (Exception e) {
                        // aucune appli pour ouvrir le lien
                    }
                    return true;
                }
                return false;
            }
        });
        web.addJavascriptInterface(new Bridge(this), "Android");
        setContentView(web);
        if (state == null) web.loadUrl("file:///android_asset/index.html");
        else web.restoreState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        Rest.hideCountdown(this);
    }

    @Override
    protected void onPause() {
        visible = false;
        Rest.showCountdown(this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
