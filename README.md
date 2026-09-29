# Asian Games Medals — India Medal Tracker (Aichi-Nagoya 2026)

Android app (WebView) showing the **Aichi–Nagoya 2026 Asian Games medal tally** with
**automatic alerts for every new medal won by India** — in-app (toast + sound) and
system notifications, including background checks roughly every 2 minutes.

Latest build: **v2.2** → [`app/AsianGamesMedals.apk`](app/AsianGamesMedals.apk)
(`versionCode 13`, package `com.example.medalapp`, min Android 7.0).

## Data sources (priority order)

1. **Official results system** — `results.asiangames2026.org` JSON API
   (`/s/AG2026/en/ALL/medals/standings` + `/s/AG2026/en/ALL/medals/org/IND`),
   fetched natively with zlib handling. Freshest data (ahead of Wikipedia/press).
2. **Wikipedia** 2026 Asian Games medal table (CORS-enabled API fallback).
3. **Bundled offline snapshot** embedded in the page (works with no internet).

## Features

- Full medal table (rank / gold / silver / bronze / total), search + sort, India highlighted
- India card with rank + medal counts
- "Latest India medals" — live event list from the official medallists feed
- Foreground alerts: toast + beep (+ system notification where supported)
- Background alerts: exact while-idle alarms → system notification even when closed,
  rescheduling + reboot support, battery-optimization prompt
- Light-mode UI, launcher icon, per-field best-seen tracking (no repeat alerts)

## Repo layout

```
app/        index.html + data.json (web app) and the signed APK
android/    AndroidManifest.xml, Java sources, launcher icon
tools/      build/verify scripts (manual v1 JAR signing, pure-Python v2 signing)
```

See [`BUILD.md`](BUILD.md) for how the APK is assembled (Termux: `ecj` + `dx` +
`aapt2` + OpenSSL CMS + hand-rolled v2 block — no Android SDK / no `apksigner` needed).

## Install

Copy the APK to the phone, tap it, allow *Install unknown apps*, allow
*Notifications* when asked, and accept the battery-optimization exemption so
background alerts keep firing. Same signing key across releases → clean updates.

## Privacy / signing

Self-signed release key (`CN=MedalApp`). The private key is **not** in this repo
(see `.gitignore`) — keep yours; updates require the same key.
