package app.railone.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fingerprints for RailOne (org.cris.aikyam).
 *
 * Every release re-obfuscates the class and method names (v2.1.58 `FUOfuro$J4Wu0l` ->
 * v2.1.62 `Qr79t$OXTLsup` -> v2.1.66 `TKhyfW$Xn1IRSv`), so nothing here may reference an
 * obfuscated name. Match on the string constants and the framework APIs the checks call
 * instead - those are stable.
 *
 * Each fingerprint below was verified to match exactly one method in v2.1.66
 * (116,418 methods across 21,574 smali files scanned).
 */

/**
 * `com.example.aikyam.AikyamApplication.onCreate()` - the only place `libnative-lib.so`
 * is loaded. That native SDK performs the anti-tamper check and calls
 * `clearApplicationUserData()` from C++ when it dislikes the build.
 *
 * The application class name itself is *not* obfuscated, so it is safe to pin exactly.
 */
object ApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/example/aikyam/AikyamApplication;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("native-lib"),
        methodCall(smali = "Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V")
    )
)

/**
 * `adb_enabled` check - read from Settings.Global and returned as a boolean.
 * Flutter calls this over the MethodChannel, independently of the native SDK.
 */
object AdbEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("adb_enabled"),
        methodCall(
            smali = "Landroid/provider/Settings\$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
        )
    )
)

/** `adb_wifi_enabled` - wireless debugging check. */
object AdbWifiEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("adb_wifi_enabled"),
        methodCall(
            smali = "Landroid/provider/Settings\$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
        )
    )
)

/**
 * `development_settings_enabled` - read with the two-argument `getInt` overload
 * (no default value), which is why it needs its own filter shape.
 */
object DevelopmentSettingsEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("development_settings_enabled"),
        methodCall(
            smali = "Landroid/provider/Settings\$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I"
        )
    )
)

/**
 * APK signature verification: hashes the signing certificate with SHA-256, Base64-encodes it
 * and compares it against an expected value supplied by Flutter (`"mySign"` parameter).
 * Returns the comparison result as a boolean.
 */
object SignatureVerificationFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    filters = listOf(
        string("mySign"),
        methodCall(smali = "Landroid/content/pm/PackageManager;->getPackageInfo(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;"),
        string("SHA-256")
    )
)

/**
 * rjsniffer's own `adb_enabled` check inside its Flutter plugin handler. It runs in a separate
 * isolated process (`:com.emrys.rjsniffer.rjsniffer.Sniffer`), so patching the main app is not
 * enough on its own.
 *
 * The library package `com/emrys/rjsniffer/rjsniffer/` is stable even though the class name is
 * not (TEEjfELP -> BoRK2H -> WiYOP6), so the fingerprint is scoped to that package.
 *
 * NOTE: `definingClass` must be a real dex type descriptor. A *leading colon* declaration like
 * ":com/emrys/rjsniffer/rjsniffer/" (which the patcher docs suggest for packages) resolves to a
 * StringComparisonType.CONTAINS match against the descriptor "Lcom/emrys/rjsniffer/rjsniffer/X;",
 * so it can never match. "Lcom/emrys/rjsniffer/rjsniffer/" (no trailing semicolon) gives a
 * STARTS_WITH match, which is what a package prefix needs.
 */
object RjsnifferAdbCheckFingerprint : Fingerprint(
    definingClass = "Lcom/emrys/rjsniffer/rjsniffer/",
    name = "onMethodCall",
    returnType = "V",
    filters = listOf(
        string("adb_enabled"),
        methodCall(
            smali = "Landroid/provider/Settings\$Secure;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I"
        ),
        // The value the check reads; the next instruction compares it against 1.
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)

/**
 * `com.example.aikyam.native.WavesActivity.onCreate()` - the app's generic in-app WebView (it reads
 * a `url` and a `headers` extra from the Intent, so every page routed through it inherits this).
 * It applies `window.setFlags(FLAG_SECURE, FLAG_SECURE)` unconditionally, which is what makes
 * screenshots of those pages come out black. The class name is not obfuscated.
 *
 * Verified: exactly one method in v2.1.66 matched `onCreate(Bundle)V`, `public`, with a `0x2000`
 * literal and a `Window.setFlags(II)` call (116,418 methods scanned).
 */
object WavesActivityFlagSecureFingerprint : Fingerprint(
    definingClass = "Lcom/example/aikyam/native/WavesActivity;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        literal(0x2000),
        methodCall(smali = "Landroid/view/Window;->setFlags(II)V")
    )
)

/**
 * The bundled `no_screenshot` Flutter plugin's FLAG_SECURE applier: the helper behind
 * `screenshotOff`, `toggleScreenshot`, `screenshotWith*` and `restoreScreenshotState`. It takes the
 * plugin's current activity, calls `Window.addFlags(0x2000)` and reports success.
 *
 * The plugin class and method names are re-obfuscated every release (`n3jvmusq/VYgyPFQ7` ->
 * `q8dcpz/CRKt1zd`), so this matches on shape only: no parameters, returns a boolean, a `0x2000`
 * literal and a `Window.addFlags(I)` call. Verified unique in v2.1.66.
 */
object PluginFlagSecureFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(0x2000),
        methodCall(smali = "Landroid/view/Window;->addFlags(I)V")
    )
)

/**
 * The same plugin's listener registration: it wires a ContentObserver onto
 * `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, whose `onChange` reports every newly saved image
 * to the Flutter layer as a "screenshot taken" event. Returning early from this method means the
 * observer is never registered, which removes the detection at its root.
 *
 * Filter order follows the instruction order in the method: the `EXTERNAL_CONTENT_URI` field read
 * comes before the `registerContentObserver` call. Verified unique in v2.1.66.
 */
object ScreenshotObserverRegistrationFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Landroid/provider/MediaStore\$Images\$Media;",
            name = "EXTERNAL_CONTENT_URI"
        ),
        methodCall(
            smali = "Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V"
        )
    )
)

