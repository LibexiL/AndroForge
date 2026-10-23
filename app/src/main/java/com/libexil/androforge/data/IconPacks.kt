package com.libexil.androforge.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val pkg: String, val label: String)

/** An ADW/Nova-compatible icon pack (appfilter.xml mapping components to drawables). */
class IconPack(private val context: Context, val pkg: String) {
    private val res: Resources? = try {
        context.packageManager.getResourcesForApplication(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** "pkg/cls" -> drawable name */
    val components: Map<String, String> by lazy { parseAppFilter() }

    val allDrawables: List<String> by lazy {
        val fromDrawableXml = parseItems("drawable", "drawable")
        (fromDrawableXml.ifEmpty { components.values.toList() }).distinct().sorted()
    }

    fun drawableFor(component: ComponentName): Drawable? =
        components["${component.packageName}/${component.className}"]?.let { load(it) }

    @SuppressLint("DiscouragedApi")
    fun load(name: String): Drawable? {
        val r = res ?: return null
        val id = r.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try {
            r.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAppFilter(): Map<String, String> {
        val map = HashMap<String, String>()
        forEachItem("appfilter") { parser ->
            val comp = parser.getAttributeValue(null, "component")
            val drawable = parser.getAttributeValue(null, "drawable")
            if (comp != null && drawable != null) {
                val key = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                if (key.contains('/')) map[key] = drawable
            }
        }
        return map
    }

    private fun parseItems(file: String, attr: String): List<String> {
        val out = ArrayList<String>()
        forEachItem(file) { parser -> parser.getAttributeValue(null, attr)?.let(out::add) }
        return out
    }

    @SuppressLint("DiscouragedApi")
    private fun forEachItem(file: String, block: (XmlPullParser) -> Unit) {
        val r = res ?: return
        try {
            val xmlId = r.getIdentifier(file, "xml", pkg)
            val parser: XmlPullParser = if (xmlId != 0) {
                r.getXml(xmlId)
            } else {
                val stream = try {
                    r.assets.open("$file.xml")
                } catch (e: Exception) {
                    return
                }
                Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") block(parser)
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w("IconPack", "Failed to parse $file in $pkg", e)
        }
    }
}

object IconPacks {
    private val loaded = HashMap<String, IconPack>()

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
        return actions.flatMap { action ->
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(action), 0)
        }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .map { IconPackInfo(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    @Synchronized
    fun get(context: Context, pkg: String): IconPack =
        loaded.getOrPut(pkg) { IconPack(context.applicationContext, pkg) }

    @Synchronized
    fun forget(pkg: String) {
        loaded.remove(pkg)
    }
}
