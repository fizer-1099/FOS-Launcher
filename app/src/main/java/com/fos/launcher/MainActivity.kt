package com.fos.launcher

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importWallpaper(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        vm.pickImageRequest = { pickImage.launch("image/*") }
        setContent { FosRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.goHome()
        }
    }
}

@Composable
fun FosRoot(vm: LauncherViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    val view = LocalView.current
    val tilt = remember { TiltSensor(ctx, s) }

    FosTheme(s) {
        val dark = isDarkTheme(s)
        val accent = MaterialTheme.colorScheme.primary
        val dir = if (Lang.fa) LayoutDirection.Rtl else LayoutDirection.Ltr

        // keep the sensor alive only while the launcher is visible
        LifecycleResumeEffect(s.tiltOn) {
            if (s.tiltOn) tilt.start()
            onPauseOrDispose { tilt.stop() }
        }

        // rebuild all icons (shape / style changes), debounced for slider dragging
        LaunchedEffect(
            s.iconShape, s.cornerRadius, s.legacyPlate, s.plateMode, s.monoIcons,
            accent, dark, vm.apps.size
        ) {
            delay(120)
            vm.rebuildIcons(
                IconStyle(
                    shape = s.iconShape,
                    radiusPct = s.cornerRadius,
                    legacyPlate = s.legacyPlate,
                    plateMode = s.plateMode,
                    mono = s.monoIcons,
                    accent = accent.toArgb(),
                    dark = dark
                )
            )
        }

        // wallpaper backdrop blur
        LaunchedEffect(s.wpBlur, vm.wpImage) {
            delay(100)
            vm.updateBlur(s.wpBlur)
        }

        // status / navigation bar icon colors follow label color
        LaunchedEffect(s.labelColorMode) {
            val act = ctx as? ComponentActivity
            val style = if (s.labelColorMode == 1) {
                SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
            } else {
                SystemBarStyle.dark(AColor.TRANSPARENT)
            }
            act?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        // optional hidden status bar
        LaunchedEffect(s.hideStatusBar) {
            val act = ctx as? ComponentActivity ?: return@LaunchedEffect
            val c = WindowCompat.getInsetsController(act.window, view)
            if (s.hideStatusBar) {
                c.hide(WindowInsetsCompat.Type.statusBars())
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                c.show(WindowInsetsCompat.Type.statusBars())
            }
        }

        BackHandler(true) {
            when {
                vm.menuApp != null -> vm.menuApp = null
                vm.renameApp != null -> vm.renameApp = null
                vm.homeMenu -> vm.homeMenu = false
                vm.drawerOpen -> vm.drawerOpen = false
                else -> vm.homeTick++
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Box(Modifier.fillMaxSize()) {
                TiltWallpaper(vm, tilt)
                HomeScreen(vm, tilt)

                val ms = animMs(s)
                AnimatedVisibility(
                    visible = vm.drawerOpen,
                    enter = slideInVertically(tween(ms)) { it / 3 } + fadeIn(tween(ms)),
                    exit = slideOutVertically(tween(ms)) { it / 3 } + fadeOut(tween(ms))
                ) { AppDrawer(vm) }

                AnimatedVisibility(
                    visible = vm.settingsOpen,
                    enter = slideInVertically(tween(ms)) { it / 6 } + fadeIn(tween(ms)),
                    exit = slideOutVertically(tween(ms)) { it / 6 } + fadeOut(tween(ms))
                ) { SettingsScreen(vm, tilt) }

                vm.menuApp?.let { AppMenu(vm, it) }
                vm.renameApp?.let { RenameDialog(vm, it) }
                if (vm.homeMenu) HomeMenu(vm)
            }
        }
    }
}
