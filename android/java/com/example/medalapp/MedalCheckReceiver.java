package com.example.medalapp;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class MedalCheckReceiver extends BroadcastReceiver {
  static final String PREF = "medals";
  static final String CH = "india_medals";
  static final long INTERVAL = 2 * 60 * 1000L;

  static void schedule(Context ctx) {
    scheduleDelayed(ctx, INTERVAL);
  }

  // One-shot alarm that reschedules itself: works with Doze (exact+while-idle when
  // permitted, otherwise Doze-aware setAndAllowWhileIdle). Never use setInexactRepeating:
  // on several OEM ROMs those stall for hours once the app is swiped away / dozing.
  static void scheduleDelayed(Context ctx, long delayMs) {
    try {
      AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
      Intent i = new Intent(ctx, MedalCheckReceiver.class);
      int flags = PendingIntent.FLAG_UPDATE_CURRENT;
      try {
        flags |= (Integer) PendingIntent.class.getField("FLAG_IMMUTABLE").get(null);
      } catch (Exception ignored) {}
      PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, flags);
      long when = System.currentTimeMillis() + Math.max(delayMs, 60 * 1000L);
      boolean exact = android.os.Build.VERSION.SDK_INT < 31;
      if (!exact) {
        try {
          Object r = AlarmManager.class.getMethod("canScheduleExactAlarms").invoke(am);
          exact = Boolean.TRUE.equals(r);
        } catch (Exception ignored) {
          exact = false;
        }
      }
      if (exact) {
        try {
          am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
          return;
        } catch (Exception ignored) {}
      }
      try {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
      } catch (Exception ignored) {}
    } catch (Exception ignored) {}
  }

  @Override public void onReceive(final Context ctx, final Intent intent) {
    final PendingResult pr = goAsync();
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          if (intent != null
              && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            schedule(ctx);
          } else {
            check(ctx);
            schedule(ctx);
          }
        } catch (Exception ignored) {}
        pr.finish();
      }
    }).start();
  }

  static void check(Context ctx) throws Exception {
    String json = MainActivity.fetchOfficial();
    JSONArray rows = new JSONArray(json);
    int g = 0, s = 0, b = 0, t = 0;
    boolean found = false;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject r = rows.getJSONObject(i);
      if ("IND".equals(r.optString("Org"))) {
        JSONObject c = r.getJSONObject("Count");
        g = c.getJSONObject("ME_GOLD").getInt("total");
        s = c.getJSONObject("ME_SILVER").getInt("total");
        b = c.getJSONObject("ME_BRONZE").getInt("total");
        t = c.getJSONObject("total").getInt("total");
        found = true;
        break;
      }
    }
    if (!found) {
      return;
    }
    SharedPreferences sp = ctx.getSharedPreferences(PREF, 0);
    // First run: record baseline silently so old medals don't re-alert.
    if (!sp.contains("t")) {
      sp.edit().putInt("g", g).putInt("s", s).putInt("b", b).putInt("t", t).apply();
      return;
    }
    int og = sp.getInt("g", 0), os = sp.getInt("s", 0),
        ob = sp.getInt("b", 0), ot = sp.getInt("t", 0);
    StringBuilder diff = new StringBuilder();
    if (g > og) {
      diff.append("+").append(g - og).append(" Gold ");
    }
    if (s > os) {
      diff.append("+").append(s - os).append(" Silver ");
    }
    if (b > ob) {
      diff.append("+").append(b - ob).append(" Bronze ");
    }
    sp.edit().putInt("g", Math.max(og, g)).putInt("s", Math.max(os, s))
        .putInt("b", Math.max(ob, b)).putInt("t", Math.max(ot, t)).apply();
    if (diff.length() > 0) {
      notify(ctx, "India wins medal! " + diff.toString().trim()
          + " — Total: " + Math.max(ot, t)
          + " (G" + Math.max(og, g) + " S" + Math.max(os, s)
          + " B" + Math.max(ob, b) + ")");
    }
  }

  static void notify(Context ctx, String msg) {
    try {
      NotificationManager nm =
          (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        NotificationChannel ch = new NotificationChannel(CH, "India medal alerts",
            NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(ch);
      }
      Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
      PendingIntent pi = null;
      if (i != null) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        try {
          flags |= (Integer) PendingIntent.class.getField("FLAG_IMMUTABLE").get(null);
        } catch (Exception ignored) {}
        pi = PendingIntent.getActivity(ctx, 0, i, flags);
      }
      Notification.Builder nb;
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        nb = new Notification.Builder(ctx, CH);
      } else {
        nb = new Notification.Builder(ctx);
      }
      nb.setContentTitle("\uD83C\uDDEE\uD83C\uDDF3 New medal for India!")
        .setContentText(msg)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setAutoCancel(true);
      if (pi != null) {
        nb.setContentIntent(pi);
      }
      try {
        nb.setStyle(new Notification.BigTextStyle().bigText(msg));
      } catch (Exception ignored) {}
      nm.notify(1, nb.build());
    } catch (Exception ignored) {}
  }
}
