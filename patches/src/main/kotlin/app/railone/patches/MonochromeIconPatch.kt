package app.railone.patches

import app.morphe.patcher.patch.resourcePatch
import app.railone.patches.shared.Constants.COMPATIBILITY_RAILONE
import org.w3c.dom.Element
import java.io.File

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/**
 * RailOne's adaptive icon (`res/mipmap-anydpi/ic_launcher.xml`) declares only `background` and
 * `foreground`, so launchers that recolour icons - Android 13+ themed icons / Material You - have no
 * monochrome layer to tint and fall back to the plain, untinted icon.
 *
 * This patch adds the missing `<monochrome>` layer. Instead of shipping a new asset it points at the
 * app's existing foreground bitmap: the system tints that layer with the theme colour by its alpha,
 * so the launcher gets a clean silhouette of the logo.
 *
 * No new resource is created and no id changes - the layer just references a mipmap that is already
 * in the APK, so `resources.arsc` needs no rewrite beyond the icon XML itself.
 */
@Suppress("unused")
val monochromeIconPatch = resourcePatch(
    name = "Monochrome icon support",
    description = "Adds the missing monochrome layer to the adaptive icon, so themed-icon launchers " +
        "(Material You) can tint RailOne's icon instead of showing it untinted.",
    default = true
) {
    compatibleWith(COMPATIBILITY_RAILONE)

    execute {
        // Discover the icon XML instead of hardcoding the path: the app ships it under the unusual
        // res/mipmap-anydpi/ (not mipmap-anydpi-v26), and a future release may move or rename it.
        val discovered = listApkEntries("res/mipmap-anydpi")
            .filter { it.substringAfterLast('/') in setOf("ic_launcher.xml", "ic_launcher_round.xml") }

        val iconPaths = discovered.ifEmpty {
            listOf("res/mipmap-anydpi-v26/ic_launcher.xml", "res/mipmap-anydpi/ic_launcher.xml")
                .filter { get(it).exists() }
        }
        if (iconPaths.isEmpty()) return@execute

        // The silhouette: the app's own foreground bitmap, referenced as a mipmap resource.
        val monochromeDrawable = listApkEntries("res/mipmap")
            .firstOrNull { it.substringAfterLast('/').startsWith("ic_launcher_adaptive_fore") }
            ?.let { "@mipmap/" + File(it).nameWithoutExtension }

        iconPaths.forEach { path ->
            val iconFile = get(path)
            if (!iconFile.exists()) return@forEach

            document(iconFile.absolutePath).use { doc ->
                // Already supports themed icons - leave it alone (re-runs must not double up).
                if (doc.getElementsByTagName("monochrome").length > 0) return@use

                val adaptiveIcon = doc.getElementsByTagName("adaptive-icon").item(0) as? Element
                    ?: return@use

                val drawable = monochromeDrawable
                    ?: (adaptiveIcon.getElementsByTagName("foreground").item(0) as? Element)
                        ?.getAttributeNS(ANDROID_NAMESPACE, "drawable")
                        ?.takeIf { it.isNotEmpty() }
                    ?: return@use

                val monochrome = doc.createElement("monochrome") as Element
                monochrome.setAttributeNS(ANDROID_NAMESPACE, "android:drawable", drawable)
                adaptiveIcon.appendChild(monochrome)
            }
        }
    }
}
