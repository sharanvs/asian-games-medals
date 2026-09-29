package com.example.medalapp;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.InflaterInputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
  WebView w;
  static final String API =
      "https://back.results.asiangames2026.org/s/AG2026/en/ALL/medals/standings";
  static final String API_MEDALLISTS =
      "https://back.results.asiangames2026.org/s/AG2026/en/ALL/medals/org/IND";
  static final String UA =
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36";

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    w = new WebView(this);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    w.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView v, String url) {
        pushOfficial();
      }
    });
    w.addJavascriptInterface(new Bridge(), "Android");
    w.loadUrl("file:///android_asset/www/index.html");
    setContentView(w);
    // Background checks every ~2 min, even when the app is closed.
    MedalCheckReceiver.schedule(this);
    ensureChannels();
    askBatteryExemption();
    // System notifications need a runtime grant on Android 13+.
    try {
      if (android.os.Build.VERSION.SDK_INT >= 33
          && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
              != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 1);
      }
    } catch (Exception ignored) {}
  }
  void ensureChannels() {
    try {
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        android.app.NotificationManager nm = (android.app.NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new android.app.NotificationChannel(
            MedalCheckReceiver.CH, "India medal alerts",
            android.app.NotificationManager.IMPORTANCE_HIGH));
      }
    } catch (Exception ignored) {}
  }

  // Ask once to exempt the app from battery optimization so background medal
  // checks keep firing while the phone dozes.
  void askBatteryExemption() {
    try {
      android.os.PowerManager pm =
          (android.os.PowerManager) getSystemService(POWER_SERVICE);
      android.content.SharedPreferences sp =
          getSharedPreferences(MedalCheckReceiver.PREF, 0);
      if (pm.isIgnoringBatteryOptimizations(getPackageName())
          || sp.getBoolean("battAsked", false)) {
        return;
      }
      sp.edit().putBoolean("battAsked", true).apply();
      Intent i = new Intent(
          android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
          android.net.Uri.parse("package:" + getPackageName()));
      startActivity(i);
    } catch (Exception ignored) {}
  }

  @Override protected void onResume() {
    super.onResume();
    // Refresh official data every time the app comes to foreground
    pushOfficial();
  }

  class Bridge {
    @JavascriptInterface
    public void refreshOfficial() {
      pushOfficial();
    }

    // Lets the page seed its best-seen baseline from native checks (avoids double alerts).
    @JavascriptInterface
    public String getBest() {
      try {
        android.content.SharedPreferences sp = getSharedPreferences(MedalCheckReceiver.PREF, 0);
        if (!sp.contains("t")) {
          return null;
        }
        return "{\"gold\":" + sp.getInt("g", 0) + ",\"silver\":" + sp.getInt("s", 0)
            + ",\"bronze\":" + sp.getInt("b", 0) + ",\"total\":" + sp.getInt("t", 0) + "}";
      } catch (Exception e) {
        return null;
      }
    }
  }

  void pushOfficial() {
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          final String json = fetchOfficial();
          String med = null;
          try {
            med = fetchUrl(API_MEDALLISTS);
          } catch (Exception ignored) {}
          final String esc = JSONObject.quote(json);
          final String escMed = (med == null) ? "null" : JSONObject.quote(med);
          runOnUiThread(new Runnable() {
            @Override public void run() {
              w.evaluateJavascript("window.onOfficialData(" + esc + "," + escMed + ")", null);
            }
          });
        } catch (final Exception e) {
          runOnUiThread(new Runnable() {
            @Override public void run() {
              try {
                w.evaluateJavascript(
                    "window.onOfficialError(" + JSONObject.quote(e.toString()) + ")", null);
              } catch (Exception ignored) {}
            }
          });
        }
      }
    }).start();
  }

  static String fetchOfficial() throws Exception {
    return fetchUrl(API);
  }

  static String fetchUrl(String url) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setRequestProperty("User-Agent", UA);
    c.setRequestProperty("Accept", "application/json");
    c.setConnectTimeout(20000);
    c.setReadTimeout(20000);
    int code = c.getResponseCode();
    String ctype = c.getContentType();
    InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0) {
      raw.write(buf, 0, n);
    }
    in.close();
    byte[] bytes = raw.toByteArray();
    // Path 1: plain JSON, or zlib-wrapped JSON.
    String ok = firstValidJson(bytes);
    if (ok != null) {
      return ok;
    }
    // Path 2: body arrived UTF-8-mangled (observed on some networks) -> reverse, then inflate.
    try {
      byte[] rev = new String(bytes, "UTF-8").getBytes("ISO-8859-1");
      ok = firstValidJson(inflate(rev));
      if (ok != null) {
        return ok;
      }
    } catch (Exception ignored) {}
    throw new Exception("official feed unusable (HTTP " + code + " " + ctype
        + " len=" + bytes.length + " head=" + hexHead(bytes) + ")");
  }

  static byte[] inflate(byte[] b) throws Exception {
    InflaterInputStream inf = new InflaterInputStream(new ByteArrayInputStream(b));
    ByteArrayOutputStream dec = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = inf.read(buf)) > 0) {
      dec.write(buf, 0, n);
    }
    return dec.toByteArray();
  }

  static String asJsonIfValid(byte[] b) {
    String s;
    try {
      s = new String(b, "UTF-8").trim();
    } catch (Exception e) {
      return null;
    }
    try {
      if (s.startsWith("[")) {
        new org.json.JSONArray(s);
        return s;
      }
      if (s.startsWith("{")) {
        new JSONObject(s);
        return s;
      }
    } catch (Exception ignored) {}
    return null;
  }

  static String firstValidJson(byte[] b) {
    String s = asJsonIfValid(b);
    if (s != null) {
      return s;
    }
    try {
      s = asJsonIfValid(inflate(b));
      if (s != null) {
        return s;
      }
    } catch (Exception ignored) {}
    return null;
  }

  static String hexHead(byte[] b) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(b.length, 12); i++) {
      if (i > 0) {
        sb.append(' ');
      }
      String h = Integer.toHexString(b[i] & 0xFF);
      if (h.length() < 2) {
        sb.append('0');
      }
      sb.append(h);
    }
    return sb.toString();
  }
}
