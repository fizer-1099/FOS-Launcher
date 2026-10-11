package com.fos.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.Locale
import kotlin.math.max

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val sp get() = settings.sp
    private val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // ---- data ----
    val apps = mutableStateListOf<AppInfo>()
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val homeItems = mutableStateListOf<String>()
    val dockItems = mutableStateListOf<String>()
    val hidden = mutableStateListOf<String>()
    val labels = mutableStateMapOf<String, String>()
    val usage = mutableStateMapOf<String, Int>()

    val visibleApps by derivedStateOf { apps.filter { it.key !in hidden } }
    val appMap by derivedStateOf { visibleApps.associateBy { it.key } }

    // ---- ui state ----
    var drawerOpen by mutableStateOf(false)
    var drawerFocus by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var settingsPage by mutableStateOf<String?>(null)
    var homeMenu by mutableStateOf(false)
    var menuApp by mutableStateOf<AppInfo?>(null)
    var renameApp by mutableStateOf<AppInfo?>(null)
    var homeTick by mutableIntStateOf(0)

    // ---- wallpaper ----
    var wpImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var wpBlurImage by mutableStateOf<ImageBitmap?>(null)
        private set
    private var wpRaw: Bitmap? = null

    var pickImageRequest: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        Lang.mode = settings.language
        hidden.addAll(loadList("list_hidden"))
        homeItems.addAll(loadList("list_home"))
        dockItems.addAll(loadList("list_dock"))
        loadMap("map_labels") { k, v -> labels[k] = v.toString() }
        loadMap("map_usage") { k, v -> usage[k] = (v as? Int) ?: 0 }
        launcherApps.registerCallback(callback)
        reload()
        viewModelScope.launch { loadWallpaper() }
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
        super.onCleared()
    }

    // ---------------------------------------------------------------- apps
    fun reload() {
        viewModelScope.launch {
            val collator = Collator.getInstance(Locale.getDefault())
            val list = withContext(Dispatchers.IO) {
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .filter { it.componentName.packageName != app.packageName }
                    .map { AppInfo(it) }
                    .sortedWith { a, b -> collator.compare(a.defaultLabel, b.defaultLabel) }
            }
            apps.clear()
            apps.addAll(list)
            if (list.isNotEmpty()) {
                if (!sp.getBoolean("home_init", false)) initDefaults(list)
                prune()
            }
        }
    }

    private fun initDefaults(list: List<AppInfo>) {
        val pm = app.packageManager
        fun pkgOf(i: Intent): String? = try {
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
        val pkgs = listOfNotNull(
            pkgOf(Intent(Intent.ACTION_DIAL)),
            pkgOf(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        ).distinct()
        var dock = pkgs.mapNotNull { p -> list.firstOrNull { it.pkg == p }?.key }
        if (dock.isEmpty()) dock = list.take(4).map { it.key }
        dockItems.clear()
        dockItems.addAll(dock)
        homeItems.clear()
        homeItems.addAll(list.map { it.key }.filter { it !in dock }.take(20))
        sp.edit().putBoolean("home_init", true).apply()
        saveLists()
    }

    private fun prune() {
        val keys = apps.map { it.key }.toSet()
        var changed = homeItems.removeAll { it !in keys }
        changed = dockItems.removeAll { it !in keys } or changed
        changed = hidden.removeAll { it !in keys } or changed
        if (changed) saveLists()
    }

    fun labelOf(a: AppInfo): String = labels[a.key] ?: a.defaultLabel

    fun launch(a: AppInfo) {
        try {
            launcherApps.startMainActivity(a.component, Process.myUserHandle(), null, null)
            usage[a.key] = (usage[a.key] ?: 0) + 1
            saveUsage()
        } catch (e: Exception) {
            toast(tr("باز کردن برنامه ممکن نبود", "Couldn't open the app"))
        }
    }

    fun sortedApps(): List<AppInfo> = when (settings.drawerSort) {
        1 -> visibleApps.sortedByDescending { usage[it.key] ?: 0 }
        2 -> visibleApps.sortedByDescending { it.installTime }
        else -> visibleApps
    }

    // ---------------------------------------------------------------- layout editing
    fun addToHome(a: AppInfo) {
        if (a.key !in homeItems) {
            homeItems.add(a.key); saveLists()
        }
    }

    fun removeFromHome(key: String) {
        homeItems.remove(key); saveLists()
    }

    fun addToDock(a: AppInfo) {
        if (a.key !in dockItems) {
            if (dockItems.size >= 6) dockItems.removeAt(dockItems.lastIndex)
            dockItems.add(a.key); saveLists()
        }
    }

    fun removeFromDock(key: String) {
        dockItems.remove(key); saveLists()
    }

    fun move(list: MutableList<String>, key: String, delta: Int) {
        val i = list.indexOf(key)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val t = list[i]; list[i] = list[j]; list[j] = t
        saveLists()
    }

    fun toggleHidden(key: String) {
        if (key in hidden) hidden.remove(key) else hidden.add(key)
        saveLists()
    }

    fun rename(a: AppInfo, name: String) {
        if (name.isBlank() || name == a.defaultLabel) labels.remove(a.key) else labels[a.key] = name.trim()
        saveMap("map_labels", labels)
    }

    // ---------------------------------------------------------------- persistence
    private fun loadList(k: String): List<String> =
        (sp.getString(k, "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun saveLists() {
        sp.edit()
            .putString("list_home", homeItems.joinToString("\n"))
            .putString("list_dock", dockItems.joinToString("\n"))
            .putString("list_hidden", hidden.joinToString("\n"))
            .apply()
    }

    private fun loadMap(k: String, put: (String, Any) -> Unit) {
        try {
            val o = JSONObject(sp.getString(k, "{}") ?: "{}")
            o.keys().forEach { key -> put(key, o.get(key)) }
        } catch (_: Exception) {
        }
    }

    private fun saveMap(k: String, m: Map<String, Any>) {
        val o = JSONObject()
        m.forEach { (a, b) -> o.put(a, b) }
        sp.edit().putString(k, o.toString()).apply()
    }

    private fun saveUsage() = saveMap("map_usage", usage)

    fun resetEverything() {
        settings.resetAll()
        Lang.mode = settings.language
        labels.clear(); usage.clear(); hidden.clear()
        sp.edit().remove("list_home").remove("list_dock").remove("list_hidden")
            .remove("map_labels").remove("map_usage").putBoolean("home_init", false).apply()
        File(app.filesDir, "wallpaper.jpg").delete()
        wpRaw = null; wpImage = null; wpBlurImage = null
        reload()
    }

    fun setLanguage(v: Int) {
        settings.language = v
        Lang.mode = v
    }

    // ---------------------------------------------------------------- icons
    suspend fun rebuildIcons(style: IconStyle) {
        val snapshot = apps.toList()
        val out = withContext(Dispatchers.Default) {
            val m = HashMap<String, ImageBitmap>()
            for (a in snapshot) {
                ensureActive()
                try {
                    val d = a.info.getIcon(0) ?: continue
                    m[a.key] = IconRenderer.render(d, style).asImageBitmap()
                } catch (_: Exception) {
                }
            }
            m
        }
        icons.putAll(out)
    }

    // ---------------------------------------------------------------- wallpaper
    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val src = ImageDecoder.createSource(app.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        val m = max(info.size.width, info.size.height)
                        if (m > 2400) decoder.setTargetSampleSize((m + 2399) / 2400)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    File(app.filesDir, "wallpaper.jpg").outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                }
                settings.wpMode = 1
                loadWallpaper()
            } catch (e: Exception) {
                toast(tr("خواندن تصویر ممکن نبود", "Couldn't read that image"))
            }
        }
    }

    suspend fun loadWallpaper() {
        val f = File(app.filesDir, "wallpaper.jpg")
        if (!f.exists()) return
        val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) } ?: return
        wpRaw = bmp
        wpImage = bmp.asImageBitmap()
        updateBlur(settings.wpBlur)
    }

    suspend fun updateBlur(level: Float) {
        val raw = wpRaw ?: return
        if (level < 0.02f) {
            wpBlurImage = null
            return
        }
        val f = 1f + level * 40f
        val out = withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(
                raw,
                max(8, (raw.width / f).toInt()),
                max(8, (raw.height / f).toInt()),
                true
            )
        }
        wpBlurImage = out.asImageBitmap()
    }

    fun removeWallpaper() {
        File(app.filesDir, "wallpaper.jpg").delete()
        wpRaw = null; wpImage = null; wpBlurImage = null
        settings.wpMode = 0
    }

    // ---------------------------------------------------------------- navigation / actions
    fun openDrawer(focus: Boolean = false) {
        drawerFocus = focus
        drawerOpen = true
    }

    fun closeAll() {
        drawerOpen = false
        settingsOpen = false
        settingsPage = null
        homeMenu = false
        menuApp = null
        renameApp = null
    }

    fun goHome() {
        closeAll()
        homeTick++
    }

    fun openSettings(page: String? = null) {
        homeMenu = false
        settingsPage = page
        settingsOpen = true
    }

    fun swipeDownAction() {
        when (settings.swipeDown) {
            0 -> expandNotifications()
            1 -> openDrawer(true)
        }
    }

    fun doubleTapAction() {
        when (settings.doubleTap) {
            1 -> openSettings()
            2 -> openDrawer(false)
        }
    }

    @Suppress("PrivateApi")
    fun expandNotifications() {
        try {
            val sbs = app.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel").invoke(sbs)
        } catch (e: Exception) {
            toast(tr("باز کردن اعلان‌ها ممکن نبود", "Couldn't open notifications"))
        }
    }

    fun openAppInfo(a: AppInfo) {
        try {
            app.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun uninstall(a: AppInfo) {
        try {
            app.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun openClockApp() {
        try {
            app.startActivity(
                Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun isDefaultLauncher(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = app.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == app.packageName
    }

    fun openDefaultLauncherSettings() {
        try {
            app.startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                app.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    fun toast(msg: String) {
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
    }
}
