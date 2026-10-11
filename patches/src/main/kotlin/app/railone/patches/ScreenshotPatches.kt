package app.railone.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.railone.patches.shared.Constants.COMPATIBILITY_RAILONE
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

/**
 * RailOne restricts and reports screenshots in two independent places:
 *
 *  1. `WavesActivity`, the app's generic in-app WebView (`url` + `headers` Intent extras), applies
 *     `FLAG_SECURE` in `onCreate`, so every page routed through it is captured black. This is the
 *     only restriction that is actually active during normal use - `MainActivity` never sets the
 *     flag, on any screen tested.
 *  2. The bundled `no_screenshot` Flutter plugin can apply the same flag on demand (`screenshotOff`,
 *     `toggleScreenshot`, ...) and registers a MediaStore ContentObserver that reports every newly
 *     saved image to the Flutter layer as a "screenshot taken" event.
 *
 * On a Moto Edge 50 Neo / Android 16 the platform still writes the PNG while the flag is set
 * (Motorola's screenshot service composites the secure layer black instead of refusing the capture),
 * so the user-visible symptom is a black screenshot rather than a "can't take screenshot" toast -
 * and the existence of a screenshot file is not proof the restriction is gone.
 *
 * Both patches rewrite the offending call site in place instead of returning early from the
 * enclosing method, so nothing else those methods do changes.
 */

/** Registers of an `invoke` instruction, in order, as absolute register numbers. */
private fun Instruction.invokeRegisters(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG)
        .take(registerCount)

    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()

    else -> throw PatchException("Expected an invoke instruction, found ${javaClass.simpleName}")
}

/**
 * Rewrite a `Window` flag call into the equivalent `clearFlags` call.
 *
 * The window register is the receiver in both cases, and the flag register is the first argument of
 * both `setFlags(II)` and `addFlags(I)`, so the same two registers can be reused. Clearing a flag
 * that was never set is a no-op, which makes this safe wherever a screen does not actually block.
 */
private fun MutableMethod.replaceFlagCallWithClearFlags(index: Int) {
    val registers = getInstruction<Instruction>(index).invokeRegisters()
    if (registers.size < 2) {
        throw PatchException("Unexpected FLAG_SECURE call shape at instruction $index")
    }

    replaceInstruction(
        index,
        "invoke-virtual {v${registers[0]}, v${registers[1]}}, Landroid/view/Window;->clearFlags(I)V"
    )
}

@Suppress("unused")
val allowScreenshotsPatch = bytecodePatch(
    name = "Allow screenshots",
    description = "Removes the FLAG_SECURE restriction that makes screenshots and screen recordings of " +
        "the app come out black. The in-app WebView (WavesActivity) stops applying it, and the bundled " +
        "no_screenshot plugin's own FLAG_SECURE hook is neutered so no screen can re-apply it.",
    default = true
) {
    compatibleWith(COMPATIBILITY_RAILONE)

    execute {
        // WavesActivity.onCreate(): setFlags(FLAG_SECURE, FLAG_SECURE) -> clearFlags(FLAG_SECURE).
        val createFlags = WavesActivityFlagSecureFingerprint.instructionMatches[1]
        WavesActivityFlagSecureFingerprint.method.replaceFlagCallWithClearFlags(createFlags.index)

        // The plugin's applier: addFlags(FLAG_SECURE) -> clearFlags(FLAG_SECURE). The method still
        // returns true, which is what the Flutter layer expects back from screenshotOff/toggle.
        val pluginFlags = PluginFlagSecureFingerprint.instructionMatches[1]
        PluginFlagSecureFingerprint.method.replaceFlagCallWithClearFlags(pluginFlags.index)
    }
}

@Suppress("unused")
val hideScreenshotDetectionPatch = bytecodePatch(
    name = "Hide screenshot detection",
    description = "Stops the bundled no_screenshot plugin from registering its MediaStore " +
        "ContentObserver, so screenshot and screen-recording detection never becomes active and no " +
        "detection event is delivered to the app's Flutter layer.",
    default = true
) {
    compatibleWith(COMPATIBILITY_RAILONE)

    execute {
        // The observer object is created when the plugin attaches to the Flutter engine; this is
        // the method that registers it against MediaStore. Returning early removes the detection
        // without touching the plugin's screenshot-prevention state.
        ScreenshotObserverRegistrationFingerprint.method.addInstructions(0, "return-void")
    }
}
