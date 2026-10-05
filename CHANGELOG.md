# Changelog

Morphe patch bundle for RailOne / Aikyam (`org.cris.aikyam`).

This file and `patches-bundle.json` are maintained by hand: this repository has no
semantic-release pipeline (the build needs a `read:packages` PAT, which should not live in a
public repo's workflow). Update both when cutting a release. Morphe Manager fetches
`CHANGELOG.md` from this repository to show what's new, derived from the manifest endpoint.

## 1.0.0 (2026-10-04)

First release. Four patches for RailOne, all enabled by default:

### Patches

- **Disable native security SDK**: stops `AikyamApplication.onCreate()` loading
  `libnative-lib.so`. That SDK is what force-stops the app and wipes its own data when it
  thinks the build is tampered with. Root cause fix; the other three only matter once this
  one is applied.
- **Bypass USB-debugging detection**: forces `adb_enabled`, `adb_wifi_enabled` and
  `development_settings_enabled` to `false`.
- **Bypass signature verification**: forces the app's own signing-certificate check
  (SHA-256 of the APK signature) to `true`, so a re-signed build is accepted.
- **Bypass rjsniffer ADB check**: neutralises the rjsniffer library's own `adb_enabled`
  check, which runs in the isolated `:com.emrys.rjsniffer.rjsniffer.Sniffer` process.

### Notes

- The fingerprints match on string constants and framework API calls (`"native-lib"` +
  `System.loadLibrary`, `"adb_enabled"` + `Settings$Global.getInt`), never on obfuscated
  class or method names, so they survive the per-release re-obfuscation.
- Verified against RailOne 2.1.66 (versionCode 237) and 2.1.62, arm64: Morphe applied all
  four patches to the real split bundle, the merged APK installed as an in-place upgrade,
  and the app stayed alive with USB debugging enabled.
- Optional companion: Morphe's official **Clone app** patch can be enabled alongside these
  (package name plus `updatePermissions` and `updateProviders`) so the stock app stays
  installed, signed in and updatable by Play while the patched build lives beside it.
