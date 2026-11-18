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
    private android.webkit.ValueCallback<android.net.Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences ui = getSharedPreferences("ui", 0);
        String st = ui.getString("st", "#7a1d2a"), nv = ui.getString("nv", "#ffffff");
        applyBars(this, st, nv);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(nv));
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
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, android.webkit.ValueCallback<android.net.Uri[]> cb,
                                             android.webkit.WebChromeClient.FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, 42);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(this), "Android");
        setContentView(web);
        if (state == null) web.loadUrl("file:///android_asset/index.html");
        else web.restoreState(state);
    }

    static void applyBars(Activity a, String status, String nav) {
        try {
            a.getWindow().setStatusBarColor(Color.parseColor(status));
            a.getWindow().setNavigationBarColor(Color.parseColor(nav));
            if (Build.VERSION.SDK_INT >= 26) {
                android.view.View d = a.getWindow().getDecorView();
                d.setSystemUiVisibility(d.getSystemUiVisibility() | 0x10);
            }
        } catch (Exception e) {
            // couleur invalide : on garde les barres actuelles
        }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        if (req == 42 && fileCb != null) {
            android.net.Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            fileCb.onReceiveValue(u != null ? new android.net.Uri[]{u} : null);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
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
