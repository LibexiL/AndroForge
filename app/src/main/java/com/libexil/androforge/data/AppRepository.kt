package com.libexil.androforge.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
)

/** An icon ready to draw. [clip] = the launcher's icon shape should be applied. */
class IconImage(val bitmap: ImageBitmap, val clip: Boolean)

class AppRepository(private val context: Context, private val store: LayoutStore) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Bumped whenever cached icons become stale (package change, icon pack change). */
    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    private val iconCache = object : LruCache<String, IconImage>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: IconImage): Int =
            value.bitmap.width * value.bitmap.height * 4
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refresh()
            store.update { pruneRemoved(it, packageName, serialOf(user)) }
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refresh()

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(pkg: String) {
        IconPacks.forget(pkg)
        invalidateIcons()
        refresh()
    }

    fun invalidateIcons() {
        iconCache.evictAll()
        _iconVersion.value = _iconVersion.value + 1
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val list = try {
                launcherApps.profiles.flatMap { user ->
                    val serial = serialOf(user)
                    launcherApps.getActivityList(null, user).map { info -> info.toAppInfo(serial) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list apps", e)
                emptyList()
            }
            _apps.value = list
                .filter { it.component.packageName != context.packageName }
                .sortedBy { it.label.lowercase() }
            _loaded.value = true
        }
    }

    private fun LauncherActivityInfo.toAppInfo(serial: Long) = AppInfo(
        key = AppKey(componentName.packageName, componentName.className, serial),
        label = label?.toString() ?: componentName.packageName,
        component = componentName,
        user = user,
    )

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userOf(key: AppKey): UserHandle =
        userManager.getUserForSerialNumber(key.user) ?: Process.myUserHandle()

    fun find(key: AppKey): AppInfo? = _apps.value.firstOrNull { it.key == key }

    fun findByPackage(pkg: String): AppInfo? = _apps.value.firstOrNull { it.key.pkg == pkg && it.key.user == 0L }
        ?: _apps.value.firstOrNull { it.key.pkg == pkg }

    // ---------------------------------------------------------------- actions

    fun launch(key: AppKey, bounds: Rect? = null): Boolean = try {
        launcherApps.startMainActivity(ComponentName(key.pkg, key.cls), userOf(key), bounds, null)
        store.update { s ->
            s.copy(
                recents = (listOf(key) + s.recents.filter { it != key }).take(12),
                launchCounts = s.launchCounts + (key.id to ((s.launchCounts[key.id] ?: 0) + 1)),
            )
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Launch failed for $key", e)
        Toast.makeText(context, "Can't open that app", Toast.LENGTH_SHORT).show()
        false
    }

    fun openAppInfo(key: AppKey) {
        try {
            launcherApps.startAppDetailsActivity(ComponentName(key.pkg, key.cls), userOf(key), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "App info failed", e)
        }
    }

    fun uninstall(key: AppKey) {
        try {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${key.pkg}"))
                .putExtra(Intent.EXTRA_USER, userOf(key))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Can't uninstall this app", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasShortcutAccess(): Boolean = try {
        launcherApps.hasShortcutHostPermission()
    } catch (e: Exception) {
        false
    }

    fun shortcuts(key: AppKey): List<ShortcutInfo> {
        if (!hasShortcutAccess()) return emptyList()
        return try {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(key.pkg)
                .setActivity(ComponentName(key.pkg, key.cls))
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            (launcherApps.getShortcuts(query, userOf(key)) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startShortcut(info: ShortcutInfo) {
        try {
            launcherApps.startShortcut(info, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- icons

    fun iconCacheKey(key: AppKey, sizePx: Int, shape: IconShape, pack: String?, custom: IconRef?): String =
        "${key.id}|$sizePx|$shape|$pack|${custom?.pack}:${custom?.drawable}"

    fun cachedIcon(cacheKey: String): IconImage? = iconCache.get(cacheKey)

    suspend fun loadIcon(key: AppKey, sizePx: Int, shape: IconShape, pack: String?, custom: IconRef?): IconImage? =
        withContext(Dispatchers.Default) {
            val cacheKey = iconCacheKey(key, sizePx, shape, pack, custom)
            iconCache.get(cacheKey)?.let { return@withContext it }
            val drawable = resolveDrawable(key, pack, custom) ?: return@withContext null
            val image = try {
                render(drawable, sizePx.coerceAtLeast(8), shape)
            } catch (e: Exception) {
                Log.w(TAG, "Icon render failed for $key", e)
                null
            }
            image?.also { iconCache.put(cacheKey, it) }
        }

    suspend fun loadPackDrawable(pack: String, name: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val d = IconPacks.get(context, pack).load(name) ?: return@withContext null
            try {
                render(d, sizePx, IconShape.SYSTEM).bitmap
            } catch (e: Exception) {
                null
            }
        }

    private fun resolveDrawable(key: AppKey, pack: String?, custom: IconRef?): Drawable? {
        custom?.let { ref -> IconPacks.get(context, ref.pack).load(ref.drawable)?.let { return it } }
        val component = ComponentName(key.pkg, key.cls)
        if (pack != null) IconPacks.get(context, pack).drawableFor(component)?.let { return it }
        return try {
            val info = launcherApps.resolveActivity(Intent().setComponent(component), userOf(key))
            info?.getBadgedIcon(0) ?: info?.getIcon(0)
        } catch (e: Exception) {
            null
        }
    }

    private fun render(drawable: Drawable, size: Int, shape: IconShape): IconImage {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val adaptive = drawable as? AdaptiveIconDrawable
        return if (adaptive != null && shape != IconShape.SYSTEM) {
            // Draw the full-bleed layers so the launcher can clip them to any shape.
            val inset = size / 4
            adaptive.background?.let {
                it.setBounds(-inset, -inset, size + inset, size + inset)
                it.draw(canvas)
            }
            adaptive.foreground?.let {
                it.setBounds(-inset, -inset, size + inset, size + inset)
                it.draw(canvas)
            }
            IconImage(bitmap.asImageBitmap(), clip = true)
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            IconImage(bitmap.asImageBitmap(), clip = false)
        }
    }

    companion object {
        private const val TAG = "AppRepository"

        fun pruneRemoved(s: LauncherState, pkg: String, user: Long): LauncherState {
            fun gone(k: AppKey) = k.pkg == pkg && k.user == user
            fun cleanList(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
                when (item) {
                    is AppItem -> if (gone(item.app)) null else item
                    is FolderItem -> {
                        val left = item.apps.filterNot(::gone)
                        when {
                            left.isEmpty() -> null
                            left.size == 1 -> AppItem(item.id, item.page, item.x, item.y, left[0])
                            else -> item.copy(apps = left)
                        }
                    }
                    is WidgetItem, is ForgeWidgetItem -> item
                }
            }
            return s.copy(
                items = cleanList(s.items),
                dock = cleanList(s.dock),
                recents = s.recents.filterNot(::gone),
            )
        }
    }
}
