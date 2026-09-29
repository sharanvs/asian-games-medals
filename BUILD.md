# BUILD — how the APK is assembled (all on-device, Termux)

No Android SDK, no Gradle, no `apksigner` (all crash or are unavailable here).
Toolchain: Termux packages `ecj`, `aapt2`/`aapt`/`zipalign`, `openjdk-21`,
`openssl-tool`, plus Google build-tools r30 `dx` (pure-Java, works) and an
`android.jar` platform.

> `d8` / `apksigner` / `keytool` / `javac` abort on this device
> (`PerfettoTrace` native-registration crash), so the pipeline routes around them:
> `ecj` compiles, legacy `dx` dexes, OpenSSL + Python sign.

## Steps (see repo root history / `tools/`)

1. Compile Java (`android/java/...`) with `ecj` against `android.jar`.
2. Dex with build-tools-30 `dx` (`--min-sdk-version=24`).
3. `aapt2 compile` (res) + `aapt2 link` manifest + assets → base APK.
4. `aapt add` the `classes.dex` (from inside its dir so the path is right).
5. **v1 (JAR) sign** — `tools/sign.js` builds `MANIFEST.MF`/`CERT.SF` over every
   zip entry, then `openssl cms -sign -nosmimecap` makes the v1 `CERT.RSA`
   (SignerInfo **v1**, issuer+serial — the default `-keyid` v3 form is rejected
   by installers). Add the three `META-INF/` files with `aapt add`.
6. **v2 sign** — `tools/v2sign.py` (pure Python, validated byte-for-byte against
   real Play/system APKs): chunked-SHA256 content digest, RSA/SHA256 signature,
   insert APK Signing Block before the Central Directory, fix the EOCD offset.
7. Verify: `tools/verify.js` (v1 digests) + `tools/v2verify.py` (v2 digest MATCH)
   + `aapt dump badging`.

Notes:

- Do **not** run this repo's `zipalign` after step 6 — some builds strip the
  Signing Block. Align before v2 insertion if needed (currently skipped;
  harmless for install).
- `tools/v2verify.py` doubles as a verifier for any APK's v2 block.
- `tools/gen_icon.py` regenerates the launcher icon (stdlib-only PNG writer).
