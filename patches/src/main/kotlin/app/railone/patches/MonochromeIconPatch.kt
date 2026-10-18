package app.railone.patches

import app.morphe.patcher.patch.resourcePatch
import app.railone.patches.shared.Constants.COMPATIBILITY_RAILONE
import org.w3c.dom.Element

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/** The mipmap the launcher tints. Shipped by this patch, one file per density. */
private const val MONOCHROME_RESOURCE = "ic_launcher_monochrome"

/** Density buckets the app already uses for its own launcher icons. */
private val LAUNCHER_DENSITIES = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

/**
 * RailOne's adaptive icon (`res/mipmap-anydpi-v26/ic_launcher.xml`) declares only `background` and
 * `foreground`. Without a `monochrome` layer, launchers that recolour icons - Android 13+ themed
 * icons / Material You - have nothing to tint and fall back to the plain icon.
 *
 * The monochrome layer has to be the logo *alone*, on transparency: the system discards the layer's
 * colour and tints it. The app's adaptive foreground is not that - it is a solid blue rounded plate
 * with the logo drawn on it, so tinting it yields a featureless block. This patch therefore carries
 * its own silhouette, generated from the app's 1024px logo mark
 * (`assets/flutter_assets/assets/icons/railone.png`) and scaled to fill 66% of the layer.
 *
 * The artwork is thin line-art, so on low density screens the strokes are fine by nature; higher
 * density devices render the larger assets.
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
        // 1. Ship the silhouette as a new mipmap per density. The patcher diffs the decoded
        //    resources, so a file written here is packaged and given a resource id on rebuild.
        //    The density directory is taken from an icon the app already ships, so the path matches
        //    the decoder's own naming (with or without the -v4 version qualifier).
        val classLoader = object {}.javaClass.classLoader
        LAUNCHER_DENSITIES.forEach { density ->
            val asset = "monochrome/${MONOCHROME_RESOURCE}_$density.png"
            val silhouette = classLoader.getResourceAsStream(asset)?.use { it.readBytes() }
                ?: return@forEach

            val existingDirectory = listOf("res/mipmap-$density-v4/ic_launcher.png", "res/mipmap-$density/ic_launcher.png")
                .firstOrNull { get(it).exists() }
                ?.substringBeforeLast('/')

            val destination = get(
                existingDirectory?.let { "$it/$MONOCHROME_RESOURCE.png" }
                    ?: "res/mipmap-$density-v4/$MONOCHROME_RESOURCE.png"
            )
            destination.parentFile?.mkdirs()
            destination.writeBytes(silhouette)
            println("[Monochrome icon] wrote ${destination.name} (${silhouette.size} bytes) to ${destination.parentFile?.name}")
        }

        // 2. Point the adaptive icon at it. Discover the XML rather than hardcoding the path: the
        //    app ships it under mipmap-anydpi-v26, and a future release may move or rename it.
        val discovered = listApkEntries("res/mipmap-anydpi")
            .filter { it.substringAfterLast('/') in setOf("ic_launcher.xml", "ic_launcher_round.xml") }

        val iconPaths = discovered.ifEmpty {
            listOf("res/mipmap-anydpi-v26/ic_launcher.xml", "res/mipmap-anydpi/ic_launcher.xml")
                .filter { get(it).exists() }
        }

        iconPaths.forEach { path ->
            val iconFile = get(path)
            if (!iconFile.exists()) return@forEach

            document(iconFile.absolutePath).use { doc ->
                // Already themed-icon capable, or a previous run of this patch: leave it be.
                if (doc.getElementsByTagName("monochrome").length > 0) return@use

                val adaptiveIcon = doc.getElementsByTagName("adaptive-icon").item(0) as? Element
                    ?: return@use

                val monochrome = doc.createElement("monochrome") as Element
                monochrome.setAttributeNS(ANDROID_NAMESPACE, "android:drawable", "@mipmap/$MONOCHROME_RESOURCE")
                adaptiveIcon.appendChild(monochrome)
            }
        }
    }
}
