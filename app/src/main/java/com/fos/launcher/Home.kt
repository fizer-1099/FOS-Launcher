@file:OptIn(ExperimentalFoundationApi::class)

package com.fos.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// ------------------------------------------------------------------ helpers

fun labelColorOf(s: Settings, accent: Color): Color = when (s.labelColorMode) {
    1 -> Color.Black
    2 -> accent
    else -> Color.White
}

fun animMs(s: Settings, base: Int = 300): Int = (base / s.animSpeed.coerceIn(0.3f, 3f)).toInt()

@Composable
fun AppIcon(
    vm: LauncherViewModel,
    app: AppInfo,
    labelColor: Color,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = vm.settings.iconSize
) {
    val s = vm.settings
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.86f else 1f, tween(animMs(s, 120)), label = "press")
    Column(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (s.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bmp = vm.icons[app.key]
        Box(Modifier.size(iconSize.dp)) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize())
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                vm.labelOf(app),
                fontSize = s.labelSize.sp,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = if (s.labelShadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f) else null
                ),
                modifier = Modifier.width((iconSize + 22).dp)
            )
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
fun HomeScreen(vm: LauncherViewModel, tilt: TiltSensor) {
    val s = vm.settings
    val accent = MaterialTheme.colorScheme.primary
    val lc = labelColorOf(s, accent)
    val map = vm.appMap
    val homeApps = vm.homeItems.mapNotNull { map[it] }
    val pageSize = max(1, s.gridCols * s.gridRows)
    val pages = max(1, ceil(homeApps.size / pageSize.toFloat()).toInt())
    val pagerState = rememberPagerState(pageCount = { pages })

    LaunchedEffect(vm.homeTick) {
        if (vm.homeTick > 0) pagerState.animateScrollToPage(0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                val threshold = 90.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -threshold) vm.openDrawer(false)
                        else if (total > threshold) vm.swipeDownAction()
                    },
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { vm.doubleTapAction() },
                    onLongPress = { vm.homeMenu = true }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .graphicsLayer {
                    val k = if (s.tiltOn) 1f else 0f
                    translationX = tilt.x * s.iconParallax * 26f * density * k
                    translationY = tilt.y * s.iconParallax * 16f * density * k
                }
        ) {
            if (s.showClock) ClockWidget(vm)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Box(
                    Modifier.graphicsLayer {
                        val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val a = 1f - abs(off).coerceIn(0f, 1f)
                        when (s.pageEffect) {
                            1 -> {
                                alpha = a
                                val z = 0.78f + 0.22f * a
                                scaleX = z; scaleY = z
                            }
                            2 -> {
                                cameraDistance = 14f * density
                                rotationY = off * 55f
                                alpha = 0.3f + 0.7f * a
                            }
                            3 -> {
                                val z = 0.85f + 0.15f * a
                                scaleX = z; scaleY = z
                                translationX = -off * size.width * 0.35f
                                alpha = 0.4f + 0.6f * a
                            }
                        }
                    }
                ) {
                    HomePage(vm, homeApps, page, lc)
                }
            }

            if (s.pageIndicator && pages > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pages) { i ->
                        val sel = i == pagerState.currentPage
                        val w by animateFloatAsState(if (sel) 18f else 7f, label = "dot")
                        val c by animateColorAsState(if (sel) Color.White else Color.White.copy(alpha = 0.4f), label = "dotc")
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(w.dp, 7.dp)
                                .clip(CircleShape)
                                .background(c)
                        )
                    }
                }
            }

            if (s.showSearchBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { vm.openDrawer(true) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("جستجوی برنامه‌ها", "Search apps"), color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                }
            }

            Dock(vm, lc)
        }
    }
}

@Composable
private fun HomePage(vm: LauncherViewModel, apps: List<AppInfo>, page: Int, lc: Color) {
    val s = vm.settings
    val cols = s.gridCols
    val rows = s.gridRows
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp)
    ) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until cols) {
                    val idx = page * cols * rows + r * cols + c
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val app = apps.getOrNull(idx)
                        if (app != null) {
                            AppIcon(
                                vm, app, lc, s.labelShow,
                                onClick = { vm.launch(app) },
                                onLongClick = { vm.menuApp = app }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(vm: LauncherViewModel, lc: Color) {
    val s = vm.settings
    val map = vm.appMap
    val dockApps = vm.dockItems.mapNotNull { map[it] }.take(s.dockCount)
    if (dockApps.isEmpty()) return
    val bg = if (s.dockBg) MaterialTheme.colorScheme.surface.copy(alpha = s.dockAlpha.coerceIn(0f, 1f)) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(s.dockRadius.dp))
            .background(bg)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dockApps.forEach { app ->
            AppIcon(
                vm, app, lc, false,
                onClick = { vm.launch(app) },
                onLongClick = { vm.menuApp = app }
            )
        }
    }
}

// ------------------------------------------------------------------ drawer

@Composable
fun AppDrawer(vm: LauncherViewModel) {
    val s = vm.settings
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (vm.drawerFocus) {
            focus.requestFocus()
            kb?.show()
        }
    }

    val list = vm.sortedApps().let { all ->
        if (query.isBlank()) all else all.filter { vm.labelOf(it).contains(query.trim(), ignoreCase = true) }
    }

    val closeConnection = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (available.y > 0f) {
                    acc += available.y
                    if (acc > 420f) {
                        acc = 0f
                        vm.drawerOpen = false
                    }
                } else acc = 0f
                return Offset.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface.copy(alpha = s.drawerAlpha.coerceIn(0.2f, 1f)))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(Modifier.fillMaxSize()) {
            // drag handle (swipe down / tap to close)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 40f) vm.drawerOpen = false },
                            onVerticalDrag = { _, dy -> total += dy }
                        )
                    }
                    .clickable { vm.drawerOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = 0.35f))
                )
            }

            // search field
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        list.firstOrNull()?.let { vm.launch(it); vm.drawerOpen = false }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(
                                tr("جستجو در برنامه‌ها", "Search apps"),
                                color = scheme.onSurfaceVariant, fontSize = 16.sp
                            )
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close, null, tint = scheme.onSurfaceVariant,
                        modifier = Modifier.clickable { query = "" }
                    )
                }
            }

            val lc = scheme.onSurface
            if (s.drawerList) {
                LazyColumn(
                    Modifier.weight(1f).nestedScroll(closeConnection),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(list, key = { it.key }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { vm.launch(app); vm.drawerOpen = false },
                                    onLongClick = { vm.menuApp = app }
                                )
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = vm.icons[app.key]
                            Box(Modifier.size(44.dp)) { if (bmp != null) Image(bmp, null, Modifier.fillMaxSize()) }
                            Spacer(Modifier.width(16.dp))
                            Text(vm.labelOf(app), color = lc, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(s.drawerCols),
                    modifier = Modifier.weight(1f).nestedScroll(closeConnection),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    items(list, key = { it.key }) { app ->
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            AppIcon(
                                vm, app, lc, s.labelShow,
                                onClick = { vm.launch(app); vm.drawerOpen = false },
                                onLongClick = { vm.menuApp = app },
                                iconSize = max(44, s.iconSize - 4)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ menus

@Composable
private fun MenuRow(text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(
        text,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    )
}

@Composable
fun AppMenu(vm: LauncherViewModel, app: AppInfo) {
    val s = vm.settings
    val close = { vm.menuApp = null }
    val inHome = app.key in vm.homeItems
    val inDock = app.key in vm.dockItems
    val locked = s.lockLayout
    Dialog(onDismissRequest = { close() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bmp = vm.icons[app.key]
                    Box(Modifier.size(44.dp)) { if (bmp != null) Image(bmp, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        vm.labelOf(app), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!locked) {
                    if (inHome) {
                        MenuRow(tr("حذف از صفحه اصلی", "Remove from home")) { vm.removeFromHome(app.key); close() }
                        MenuRow(tr("انتقال به قبل", "Move earlier")) { vm.move(vm.homeItems, app.key, -1) }
                        MenuRow(tr("انتقال به بعد", "Move later")) { vm.move(vm.homeItems, app.key, 1) }
                    } else {
                        MenuRow(tr("افزودن به صفحه اصلی", "Add to home")) { vm.addToHome(app); close() }
                    }
                    if (inDock) {
                        MenuRow(tr("حذف از داک", "Remove from dock")) { vm.removeFromDock(app.key); close() }
                        MenuRow(tr("انتقال در داک (قبل)", "Move earlier in dock")) { vm.move(vm.dockItems, app.key, -1) }
                        MenuRow(tr("انتقال در داک (بعد)", "Move later in dock")) { vm.move(vm.dockItems, app.key, 1) }
                    } else {
                        MenuRow(tr("افزودن به داک", "Add to dock")) { vm.addToDock(app); close() }
                    }
                }
                MenuRow(tr("تغییر نام", "Rename")) { vm.renameApp = app; close() }
                MenuRow(tr("پنهان کردن برنامه", "Hide app")) { vm.toggleHidden(app.key); close() }
                MenuRow(tr("اطلاعات برنامه", "App info")) { vm.openAppInfo(app); close() }
                MenuRow(tr("حذف برنامه", "Uninstall"), MaterialTheme.colorScheme.error) { vm.uninstall(app); close() }
            }
        }
    }
}

@Composable
fun RenameDialog(vm: LauncherViewModel, app: AppInfo) {
    var text by remember { mutableStateOf(vm.labelOf(app)) }
    AlertDialog(
        onDismissRequest = { vm.renameApp = null },
        title = { Text(tr("تغییر نام برنامه", "Rename app")) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { vm.rename(app, text); vm.renameApp = null }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = { vm.renameApp = null }) { Text(tr("انصراف", "Cancel")) }
        }
    )
}

@Composable
fun HomeMenu(vm: LauncherViewModel) {
    Dialog(onDismissRequest = { vm.homeMenu = false }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(10.dp)) {
                MenuRow(tr("🖼️  والپیپر و تیلت", "🖼️  Wallpaper & tilt")) { vm.openSettings("wallpaper") }
                MenuRow(tr("🎨  آیکون‌ها", "🎨  Icons")) { vm.openSettings("icons") }
                MenuRow(tr("🏠  صفحه اصلی", "🏠  Home screen")) { vm.openSettings("home") }
                MenuRow(tr("⚙️  تنظیمات F OS", "⚙️  F OS settings")) { vm.openSettings(null) }
            }
        }
    }
}
