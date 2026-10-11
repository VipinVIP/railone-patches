# Changelog

## 1.1.0 (2026-10-11)

Two new patches that remove the app's screenshot restrictions. Everything from 1.0.0 is unchanged
and the bundle still installs over the previous build (same keystore, data preserved).

### Patches

- **Allow screenshots.** `WavesActivity` - the app's generic in-app WebView (it takes a `url` and
  `headers` extra, so every page routed through it inherits this) applies `FLAG_SECURE` in
  `onCreate`, which made screenshots of those pages come out black. The patch rewrites that call
  into `clearFlags`, and neuters the bundled `no_screenshot` Flutter plugin's own `FLAG_SECURE`
  hook (`screenshotOff`, `toggleScreenshot`, `screenshotWith*`) so no screen can re-apply it.
- **Hide screenshot detection.** The same plugin registers a MediaStore `ContentObserver` that
  reports every newly saved image to the Flutter layer as a "screenshot taken" event. The patch
  returns early from the registration, so detection is never active.

### Notes

- On Motorola firmware the platform still writes the PNG while `FLAG_SECURE` is set, compositing
  the secure layer black, so the user-visible bug was a *black screenshot* rather than a "can't
  take screenshot" refusal - a screenshot file existing is not proof that the flag is gone.
- Verified on RailOne 2.1.66 (versionCode 237), arm64, Moto Edge 50 Neo / Android 16: screenshots
  of the Home, PNR and in-app WebView screens all come out with real content, and no detection
  event is logged.
- `MainActivity` does not set `FLAG_SECURE` on any screen tested. The app's Protectt RASP SDK has
  its own capture hooks and rule-driven appliers, but none were active, so they are deliberately
  left unpatched rather than half-covered.

## 1.0.0 (2026-10-04)

First release: four patches for RailOne (org.cris.aikyam), all enabled by default.

### Patches

- Disable native security SDK. Stops the app loading libnative-lib.so, the native
  anti-tamper SDK that force-stops the app and wipes its own data on a re-signed build.
- Bypass USB-debugging detection. Forces the adb_enabled, adb_wifi_enabled and
  development_settings_enabled checks to return false.
- Bypass signature verification. Forces the app's own signing-certificate check to return
  true, so a re-signed build is accepted.
- Bypass rjsniffer ADB check. Neutralises the rjsniffer library's own adb_enabled check,
  which runs in its isolated Sniffer process.

### Notes

- Verified on RailOne 2.1.66 (versionCode 237) and 2.1.62, arm64.
- Fingerprints match on string constants and framework API calls, never on obfuscated class
  or method names, so they survive the per-release re-obfuscation.
- Morphe's own Clone app patch can be enabled alongside these, so the stock app stays
  installed, signed in and updatable by Play while the patched build lives beside it.
