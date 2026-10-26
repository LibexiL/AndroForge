package com.libexil.androforge

import android.Manifest
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import com.libexil.androforge.data.WidgetItem
import com.libexil.androforge.data.ForgePalette
import com.libexil.androforge.data.Palettes
import com.libexil.androforge.live.ForgeNotificationListener
import com.libexil.androforge.live.PermissionBus
import android.content.ComponentName
import com.libexil.androforge.ui.LauncherScreen
import com.libexil.androforge.update.UpdateNotifier

class MainActivity : ComponentActivity(), LauncherHost {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private val widgetViews = HashMap<Int, AppWidgetHostView>()

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingWidgetPage = 0

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (id == -1) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrPlace(id) else cancelPendingWidget()
    }

    private val requestHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val runtimePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        PermissionBus.changed()
        if (!granted) {
            Toast.makeText(this, "Permission denied - you can allow it in App info → Permissions", Toast.LENGTH_LONG).show()
        }
    }

    private var paletteExport: ForgePalette? = null

    private val importPaletteDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.replace('-', ' ')
            ?.replaceFirstChar { it.uppercase() } ?: "Imported"
        val palette = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }?.let { Palettes.parseThemeFile(it, name) }
        } catch (e: Exception) {
            null
        }
        if (palette != null) {
            vm.addCustomPalette(palette)
            Toast.makeText(this, "Theme \u201c${palette.name}\u201d added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "That isn't a ThemeForge theme file", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bmp = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (e: Exception) {
            null
        }
        if (bmp == null) {
            Toast.makeText(this, "Couldn't open that image", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val stamp = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())
        vm.addCustomPalette(Palettes.fromBitmap(bmp, "Image $stamp"))
        vm.updateSettings { it.copy(themeMode = com.libexil.androforge.data.ThemeMode.PALETTE, dockAdaptiveColor = true) }
        Toast.makeText(this, "Theme made from your image", Toast.LENGTH_SHORT).show()
    }

    private var iconPhotoFor: com.libexil.androforge.data.AppKey? = null

    private val pickIcon = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = iconPhotoFor ?: return@registerForActivityResult
        iconPhotoFor = null
        if (uri == null) return@registerForActivityResult
        val bmp = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (e: Exception) {
            null
        }
        val ref = bmp?.let { vm.repo.savePhotoIcon(it) }
        if (ref == null) {
            Toast.makeText(this, "Couldn't use that image", Toast.LENGTH_SHORT).show()
        } else {
            vm.setIcon(key, ref)
            Toast.makeText(this, "Icon updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun pickIconPhoto(key: com.libexil.androforge.data.AppKey) {
        iconPhotoFor = key
        pickIcon.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun authenticate(title: String, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Older Android: use the lock screen check.
            val km = getSystemService(android.app.KeyguardManager::class.java)
            if (km == null || !km.isDeviceSecure) {
                onSuccess()
                return
            }
            pendingUnlock = onSuccess
            @Suppress("DEPRECATION")
            val i = km.createConfirmDeviceCredentialIntent(title, null)
            if (i == null) onSuccess() else confirmCredential.launch(i)
            return
        }
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (km != null && !km.isDeviceSecure) {
            Toast.makeText(this, "Set a screen lock to protect private apps", Toast.LENGTH_SHORT).show()
            onSuccess()
            return
        }
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", mainExecutor) { _, _ -> }
        }
        try {
            builder.build().authenticate(
                android.os.CancellationSignal(),
                mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        onSuccess()
                    }
                },
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't show the fingerprint prompt", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingUnlock: (() -> Unit)? = null
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val ok = pendingUnlock
        pendingUnlock = null
        if (r.resultCode == RESULT_OK) ok?.invoke()
    }

    /** Shake while AndroForge is open (the accessibility service handles it everywhere else). */
    private val shake by lazy {
        com.libexil.androforge.live.ShakeDetector(this, { vm.state.value.settings.shakeSensitivity }) {
            vm.emit(UiEvent.Gesture(com.libexil.androforge.data.GestureSlot.SHAKE))
        }
    }

    override fun onPause() {
        super.onPause()
        shake.stop()
    }

    override fun onDestroy() {
        if (vm.host === this) vm.host = null
        super.onDestroy()
    }

    override fun pickPaletteImage() {
        pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private val exportPaletteDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val p = paletteExport ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(Palettes.toThemeFile(p).toByteArray()) }
            Toast.makeText(this, "Theme saved - open it in ThemeForge with Import", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(vm.store.exportJson().toByteArray()) }
            Toast.makeText(this, "Layout exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            text != null && vm.store.importJson(text)
        } catch (e: Exception) {
            false
        }
        if (ok) vm.repo.invalidateIcons()
        Toast.makeText(this, if (ok) "Layout restored" else "That file isn't an AndroForge backup", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false

        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, WIDGET_HOST_ID)

        vm.host = this
        setContent { LauncherScreen(vm, this) }

        handleIntent(intent, fresh = true)
        askForNotificationsOnce()
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        vm.maybeAutoCheck()
        vm.resumeWallpaperMatch()
        (application as LauncherApp).onWallpaperMaybeChanged()
        if (!LockService.isRunning && vm.state.value.settings.shake != com.libexil.androforge.data.GestureAction.NONE) shake.start()
        // Coming back from a settings screen may have changed access.
        PermissionBus.changed()
        com.libexil.androforge.live.SystemOverride.apply(this, vm.state.value.settings)
        val st = vm.state.value.settings
        if ((st.hideSystemPopups || st.hideSystemIcons) && !com.libexil.androforge.live.SystemOverride.canWrite(this) && !warnedOverride) {
            warnedOverride = true
            Toast.makeText(this, "AndroForge lost permission to hide Android's pop-ups \u2014 run the adb command in Settings again", Toast.LENGTH_LONG).show()
        }
    }

    private var warnedOverride = false

    override fun onStop() {
        super.onStop()
        try {
            widgetHost.stopListening()
        } catch (_: Exception) {
        }
        vm.store.flush()
        vm.maybeAutoBackup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, fresh = false)
    }

    private fun handleIntent(intent: Intent?, fresh: Boolean) {
        if (intent == null) return
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_SHOW_UPDATE, false)) {
            intent.removeExtra(UpdateNotifier.EXTRA_SHOW_UPDATE)
            vm.checkForUpdates(manual = true)
            return
        }
        if (!fresh && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val alreadyHome = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (alreadyHome) {
                // Home pressed while already on the home screen: close things, then go to page 0.
                val anything = vm.openFolderId != null || vm.settingsOpen || vm.menu != null
                vm.closeEverything()
                if (!anything) vm.emit(UiEvent.ScrollToPage(0))
            } else {
                vm.closeEverything()
            }
        }
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("androforge", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ------------------------------------------------------------------ LauncherHost

    override fun widgetView(item: WidgetItem): View? {
        widgetViews[item.widgetId]?.let { return it }
        val info = widgetManager.getAppWidgetInfo(item.widgetId) ?: return null
        return try {
            widgetHost.createView(this, item.widgetId, info).also { widgetViews[item.widgetId] = it }
        } catch (e: Exception) {
            null
        }
    }

    override fun pickWidget(info: AppWidgetProviderInfo, page: Int) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingWidgetInfo = info
        pendingWidgetPage = page
        val bound = try {
            widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) {
            false
        }
        if (bound) {
            configureOrPlace(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try {
                bindWidget.launch(intent)
            } catch (e: Exception) {
                cancelPendingWidget()
                Toast.makeText(this, "Couldn't add that widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrPlace(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id) ?: pendingWidgetInfo
        if (info == null) {
            cancelPendingWidget()
            return
        }
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Some providers don't allow external configuration; place it unconfigured.
            }
        }
        placePendingWidget(id, info)
    }

    private fun placePendingWidget(id: Int, info: AppWidgetProviderInfo) {
        vm.placeWidget(id, info, pendingWidgetPage)
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != -1) {
            try {
                widgetHost.deleteAppWidgetId(pendingWidgetId)
            } catch (_: Exception) {
            }
        }
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE) return
        val id = pendingWidgetId
        if (id == -1) return
        val info = widgetManager.getAppWidgetInfo(id) ?: pendingWidgetInfo
        if (resultCode == RESULT_OK && info != null) placePendingWidget(id, info) else cancelPendingWidget()
    }

    override fun deleteWidget(widgetId: Int) {
        widgetViews.remove(widgetId)
        try {
            widgetHost.deleteAppWidgetId(widgetId)
        } catch (_: Exception) {
        }
    }

    override fun pickWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(this, "No wallpaper picker found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val res = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    override fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    requestHome.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (_: Exception) {
                }
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun exportLayout() = exportDoc.launch("androforge-layout.json")

    override fun requestPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            PermissionBus.changed()
        } else {
            runtimePermission.launch(permission)
        }
    }

    override fun openNotificationAccess() {
        val component = ComponentName(this, ForgeNotificationListener::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Toast.makeText(this, "Turn on AndroForge", Toast.LENGTH_SHORT).show()
    }

    override fun openAccessibility() {
        SystemActions.openAccessibilitySettings(this)
        Toast.makeText(this, "Open \u201cAndroForge system features\u201d and turn it on", Toast.LENGTH_LONG).show()
    }

    override fun importPalette() = importPaletteDoc.launch(arrayOf("application/json", "text/plain", "*/*"))

    override fun exportPalette(palette: ForgePalette) {
        paletteExport = palette
        exportPaletteDoc.launch(palette.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ".json")
    }

    override fun importLayout() = importDoc.launch(arrayOf("application/json", "text/plain", "*/*"))

    companion object {
        private const val WIDGET_HOST_ID = 0xAF0
        private const val REQUEST_CONFIGURE = 4711
    }
}
