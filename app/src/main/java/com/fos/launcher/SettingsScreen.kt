@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.fos.launcher

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

// ------------------------------------------------------------------ building blocks

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    label: (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(label(value), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun IntSliderRow(title: String, value: Int, min: Int, max: Int, suffix: String = "", onChange: (Int) -> Unit) {
    SliderRow(
        title, value.toFloat(), min.toFloat()..max.toFloat(), steps = (max - min - 1).coerceAtLeast(0),
        label = { "${it.roundToInt()}$suffix" }
    ) { onChange(it.roundToInt()) }
}

@Composable
private fun PercentSliderRow(title: String, value: Float, min: Float = 0f, max: Float = 1f, onChange: (Float) -> Unit) {
    SliderRow(title, value, min..max, label = { "${(it * 100).roundToInt()}%" }, onChange = onChange)
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
            }
        }
    }
}

@Composable
private fun ClickRow(title: String, sub: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) { content() }
    }
}

// ------------------------------------------------------------------ root

@Composable
fun SettingsScreen(vm: LauncherViewModel, tilt: TiltSensor) {
    val page = vm.settingsPage
    BackHandler {
        if (page != null) vm.settingsPage = null else vm.settingsOpen = false
    }
    Surface(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        val back = { vm.settingsPage = null }
        when (page) {
            "wallpaper" -> SettingsPage(tr("والپیپر و تیلت", "Wallpaper & tilt"), back) { WallpaperPage(vm, tilt) }
            "icons" -> SettingsPage(tr("آیکون‌ها", "Icons"), back) { IconsPage(vm) }
            "home" -> SettingsPage(tr("صفحه اصلی", "Home screen"), back) { HomePageSettings(vm) }
            "drawer" -> SettingsPage(tr("کشوی برنامه‌ها", "App drawer"), back) { DrawerPage(vm) }
            "theme" -> SettingsPage(tr("تم و رنگ", "Theme & colors"), back) { ThemePage(vm) }
            "gestures" -> SettingsPage(tr("ژست‌ها و انیمیشن", "Gestures & motion"), back) { GesturesPage(vm) }
            "hidden" -> HiddenPage(vm, back)
            "about" -> SettingsPage(tr("زبان و درباره", "Language & about"), back) { AboutPage(vm) }
            else -> SettingsPage(tr("تنظیمات F OS", "F OS settings"), { vm.settingsOpen = false }) { MainList(vm) }
        }
    }
}

@Composable
private fun MainList(vm: LauncherViewModel) {
    if (!vm.isDefaultLauncher()) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)
        ) {
            Text(
                tr("F OS هنوز لانچر پیش‌فرض نیست", "F OS isn't your default launcher yet"),
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.openDefaultLauncherSettings() }) {
                Text(tr("انتخاب به‌عنوان لانچر", "Set as default"))
            }
        }
    }
    ClickRow(tr("🖼️  والپیپر و تیلت", "🖼️  Wallpaper & tilt"), tr("افکت عمق و پرسپکتیو مثل Solo Tilt", "Depth & perspective effect, Solo Tilt style")) { vm.settingsPage = "wallpaper" }
    ClickRow(tr("🎨  آیکون‌ها", "🎨  Icons"), tr("شکل، گوشه‌ها، اندازه و برچسب", "Shape, corners, size and labels")) { vm.settingsPage = "icons" }
    ClickRow(tr("🏠  صفحه اصلی", "🏠  Home screen"), tr("شبکه، داک، ساعت، انیمیشن صفحات", "Grid, dock, clock, page effects")) { vm.settingsPage = "home" }
    ClickRow(tr("📚  کشوی برنامه‌ها", "📚  App drawer"), tr("ستون‌ها، مرتب‌سازی، شفافیت", "Columns, sorting, opacity")) { vm.settingsPage = "drawer" }
    ClickRow(tr("🌈  تم و رنگ", "🌈  Theme & colors"), tr("تاریک/روشن، رنگ اصلی، فونت", "Dark/light, accent, font")) { vm.settingsPage = "theme" }
    ClickRow(tr("👆  ژست‌ها و انیمیشن", "👆  Gestures & motion"), tr("کشیدن، دوبار ضربه، سرعت انیمیشن", "Swipes, double tap, animation speed")) { vm.settingsPage = "gestures" }
    ClickRow(tr("🙈  برنامه‌های پنهان", "🙈  Hidden apps"), "${vm.hidden.size}") { vm.settingsPage = "hidden" }
    ClickRow(tr("ℹ️  زبان و درباره", "ℹ️  Language & about")) { vm.settingsPage = "about" }
}

// ------------------------------------------------------------------ pages

@Composable
private fun WallpaperPage(vm: LauncherViewModel, tilt: TiltSensor) {
    val s = vm.settings
    Box(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        TiltWallpaper(vm, tilt, Modifier.fillMaxSize())
        Text(
            tr("گوشی را کج کنید", "Tilt your phone"),
            color = Color.White, fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
        )
    }
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { vm.pickImageRequest?.invoke() }) { Text(tr("انتخاب تصویر", "Choose image")) }
        if (s.wpMode == 1 && vm.wpImage != null) {
            OutlinedButton(onClick = { vm.removeWallpaper() }) { Text(tr("حذف تصویر", "Remove")) }
        }
    }

    SectionTitle(tr("طرح‌های آماده", "Presets"))
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WallpaperPresetNames.forEachIndexed { i, n ->
            FilterChip(
                selected = s.wpMode == 0 && s.wpPreset == i,
                onClick = { s.wpPreset = i; s.wpMode = 0 },
                label = { Text(tr(n.first, n.second)) }
            )
        }
    }

    SectionTitle(tr("افکت تیلت", "Tilt effect"))
    SwitchRow(tr("فعال‌سازی تیلت", "Enable tilt"), s.tiltOn) { s.tiltOn = it }
    PercentSliderRow(tr("عمق", "Depth"), s.tiltDepth) { s.tiltDepth = it }
    SliderRow(tr("پرسپکتیو (زاویه)", "Perspective angle"), s.tiltPersp, 0f..30f, label = { "${it.roundToInt()}°" }) { s.tiltPersp = it }
    PercentSliderRow(tr("محو بودن پس‌زمینه (بلور)", "Backdrop blur"), s.wpBlur) { s.wpBlur = it }
    SliderRow(tr("بزرگ‌نمایی تصویر", "Image zoom"), s.wpZoom, 1f..1.6f, label = { String.format(Locale.US, "%.2fx", it) }) { s.wpZoom = it }
    SliderRow(tr("حساسیت حرکت", "Sensitivity"), s.tiltSens, 0.5f..4f, label = { String.format(Locale.US, "%.1fx", it) }) { s.tiltSens = it }
    SliderRow(tr("نرمی حرکت", "Smoothing"), s.tiltSmooth, 0f..0.95f) { s.tiltSmooth = it }
    PercentSliderRow(tr("تیرگی والپیپر", "Dim"), s.wpDim, 0f, 0.8f) { s.wpDim = it }
    PercentSliderRow(tr("حرکت پارالاکس آیکون‌ها", "Icon parallax"), s.iconParallax) { s.iconParallax = it }
    SwitchRow(tr("درخشش (Glare)", "Glare"), s.tiltGlare) { s.tiltGlare = it }
    SwitchRow(tr("معکوس کردن محور افقی", "Invert horizontal"), s.invX) { s.invX = it }
    SwitchRow(tr("معکوس کردن محور عمودی", "Invert vertical"), s.invY) { s.invY = it }
    SwitchRow(
        tr("مرکزگذاری خودکار", "Auto-center"), s.autoCenter,
        tr("زاویه‌ی فعلی گوشی به‌تدریج مرکز می‌شود", "Slowly adopts your current holding angle as neutral")
    ) { s.autoCenter = it }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        OutlinedButton(onClick = { tilt.recenter() }) { Text(tr("مرکز کردن همین الان", "Recenter now")) }
    }
}

@Composable
private fun IconsPage(vm: LauncherViewModel) {
    val s = vm.settings
    val sample = vm.visibleApps.take(5)
    Row(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sample.forEach { a ->
            val b = vm.icons[a.key]
            Box(Modifier.size(minOf(s.iconSize, 56).dp)) {
                if (b != null) Image(b, null, Modifier.fillMaxSize())
            }
        }
    }
    ChoiceRow(
        tr("شکل آیکون", "Icon shape"),
        listOf(tr("گرد/گوشه‌دار", "Rounded"), tr("اسکوئرکل", "Squircle"), tr("قطره", "Teardrop"), tr("شش‌ضلعی", "Hexagon")),
        s.iconShape
    ) { s.iconShape = it }
    if (s.iconShape == 0) IntSliderRow(tr("گردی گوشه‌ها", "Corner roundness"), s.cornerRadius, 0, 50, "%") { s.cornerRadius = it }
    IntSliderRow(tr("اندازه آیکون", "Icon size"), s.iconSize, 40, 80, "dp") { s.iconSize = it }
    SwitchRow(
        tr("بستر برای آیکون‌های قدیمی", "Plate behind legacy icons"), s.legacyPlate,
        tr("آیکون‌های غیر Adaptive روی یک زمینه قرار می‌گیرند تا بریده نشوند", "Non-adaptive icons sit on a plate instead of being cropped")
    ) { s.legacyPlate = it }
    if (s.legacyPlate) ChoiceRow(
        tr("رنگ بستر", "Plate color"),
        listOf(tr("خودکار", "Auto"), tr("تیره", "Dark"), tr("رنگ اصلی", "Accent")), s.plateMode
    ) { s.plateMode = it }
    SwitchRow(
        tr("آیکون‌های تک‌رنگ (Themed)", "Themed monochrome icons"), s.monoIcons,
        if (Build.VERSION.SDK_INT >= 33) tr("برای برنامه‌هایی که پشتیبانی می‌کنند", "For apps that support it")
        else tr("نیازمند اندروید ۱۳ یا بالاتر", "Needs Android 13+")
    ) { s.monoIcons = it }

    SectionTitle(tr("برچسب‌ها", "Labels"))
    SwitchRow(tr("نمایش نام برنامه", "Show app names"), s.labelShow) { s.labelShow = it }
    SliderRow(tr("اندازه نوشته", "Label size"), s.labelSize, 8f..16f, steps = 7, label = { "${it.roundToInt()}sp" }) { s.labelSize = it }
    ChoiceRow(
        tr("رنگ نوشته", "Label color"),
        listOf(tr("سفید", "White"), tr("مشکی", "Black"), tr("رنگ اصلی", "Accent")), s.labelColorMode
    ) { s.labelColorMode = it }
    SwitchRow(tr("سایه نوشته", "Label shadow"), s.labelShadow) { s.labelShadow = it }
}

@Composable
private fun HomePageSettings(vm: LauncherViewModel) {
    val s = vm.settings
    SectionTitle(tr("شبکه", "Grid"))
    IntSliderRow(tr("تعداد ستون", "Columns"), s.gridCols, 3, 7) { s.gridCols = it }
    IntSliderRow(tr("تعداد ردیف", "Rows"), s.gridRows, 3, 8) { s.gridRows = it }
    SwitchRow(tr("قفل چیدمان", "Lock layout"), s.lockLayout, tr("جلوگیری از تغییر ناخواسته", "Prevents accidental edits")) { s.lockLayout = it }

    SectionTitle(tr("داک", "Dock"))
    IntSliderRow(tr("تعداد آیکون داک", "Dock icons"), s.dockCount, 3, 6) { s.dockCount = it }
    SwitchRow(tr("پس‌زمینه داک", "Dock background"), s.dockBg) { s.dockBg = it }
    if (s.dockBg) {
        PercentSliderRow(tr("شفافیت داک", "Dock opacity"), s.dockAlpha) { s.dockAlpha = it }
        IntSliderRow(tr("گردی داک", "Dock corner radius"), s.dockRadius, 0, 44, "dp") { s.dockRadius = it }
    }

    SectionTitle(tr("ساعت", "Clock"))
    SwitchRow(tr("نمایش ساعت", "Show clock"), s.showClock) { s.showClock = it }
    if (s.showClock) {
        ChoiceRow(
            tr("سبک ساعت", "Clock style"),
            listOf(tr("بزرگ", "Large"), tr("فشرده", "Compact"), tr("دو ردیفه", "Split")), s.clockStyle
        ) { s.clockStyle = it }
        ChoiceRow(tr("چینش", "Alignment"), listOf(tr("شروع", "Start"), tr("وسط", "Center")), s.clockAlign) { s.clockAlign = it }
        SliderRow(tr("اندازه ساعت", "Clock size"), s.clockSize, 32f..110f, label = { "${it.roundToInt()}sp" }) { s.clockSize = it }
        SwitchRow(tr("قالب ۲۴ ساعته", "24-hour format"), s.clock24) { s.clock24 = it }
        SwitchRow(tr("نمایش تاریخ", "Show date"), s.showDate) { s.showDate = it }
        SwitchRow(tr("تاریخ شمسی", "Persian (Jalali) calendar"), s.jalali) { s.jalali = it }
    }

    SectionTitle(tr("ظاهر", "Appearance"))
    SwitchRow(tr("نوار جستجو", "Search bar"), s.showSearchBar) { s.showSearchBar = it }
    SwitchRow(tr("نقطه‌های صفحه", "Page indicator"), s.pageIndicator) { s.pageIndicator = it }
    ChoiceRow(
        tr("انیمیشن جابه‌جایی صفحه", "Page transition"),
        listOf(tr("ساده", "Slide"), tr("زوم و محو", "Zoom fade"), tr("مکعبی", "Cube"), tr("عمق", "Depth")), s.pageEffect
    ) { s.pageEffect = it }
    SwitchRow(tr("پنهان کردن نوار وضعیت", "Hide status bar"), s.hideStatusBar) { s.hideStatusBar = it }
}

@Composable
private fun DrawerPage(vm: LauncherViewModel) {
    val s = vm.settings
    ChoiceRow(tr("نمایش", "Layout"), listOf(tr("شبکه", "Grid"), tr("لیست", "List")), if (s.drawerList) 1 else 0) { s.drawerList = it == 1 }
    if (!s.drawerList) IntSliderRow(tr("تعداد ستون", "Columns"), s.drawerCols, 3, 6) { s.drawerCols = it }
    ChoiceRow(
        tr("مرتب‌سازی", "Sort"),
        listOf(tr("الفبایی", "A–Z"), tr("پراستفاده‌ترین", "Most used"), tr("جدیدترین", "Newest")), s.drawerSort
    ) { s.drawerSort = it }
    PercentSliderRow(tr("غیرشفافی پس‌زمینه", "Background opacity"), s.drawerAlpha, 0.2f, 1f) { s.drawerAlpha = it }
}

private val AccentHues = listOf(0f, 22f, 45f, 120f, 165f, 200f, 220f, 255f, 285f, 325f)

@Composable
private fun ThemePage(vm: LauncherViewModel) {
    val s = vm.settings
    ChoiceRow(
        tr("حالت تم", "Theme mode"),
        listOf(tr("سیستم", "System"), tr("روشن", "Light"), tr("تاریک", "Dark")), s.themeMode
    ) { s.themeMode = it }
    if (Build.VERSION.SDK_INT >= 31) {
        SwitchRow(tr("رنگ‌های پویا (Material You)", "Dynamic colors (Material You)"), s.useDynamic) { s.useDynamic = it }
    }
    SectionTitle(tr("رنگ اصلی", "Accent color"))
    FlowRow(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccentHues.forEach { h ->
            val c = Color.hsv(h, s.accentSat.coerceIn(0f, 1f), 0.95f)
            val sel = kotlin.math.abs(s.accentHue - h) < 1f
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c)
                    .clickable { s.accentHue = h; s.useDynamic = false },
                contentAlignment = Alignment.Center
            ) { if (sel) Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White)) }
        }
    }
    SliderRow(tr("طیف رنگ", "Hue"), s.accentHue, 0f..360f, label = { "${it.roundToInt()}°" }) { s.accentHue = it; s.useDynamic = false }
    PercentSliderRow(tr("اشباع رنگ", "Saturation"), s.accentSat) { s.accentSat = it }

    SectionTitle(tr("فونت", "Font"))
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val names = listOf(tr("پیش‌فرض", "Default"), "Sans", "Serif", "Mono", "Cursive")
        names.forEachIndexed { i, n ->
            FilterChip(
                selected = s.fontFamily == i,
                onClick = { s.fontFamily = i },
                label = { Text(n, fontFamily = fontOf(i)) }
            )
        }
    }
}

@Composable
private fun GesturesPage(vm: LauncherViewModel) {
    val s = vm.settings
    ChoiceRow(
        tr("کشیدن به پایین روی صفحه اصلی", "Swipe down on home"),
        listOf(tr("اعلان‌ها", "Notifications"), tr("جستجو", "Search"), tr("هیچ", "Nothing")), s.swipeDown
    ) { s.swipeDown = it }
    ChoiceRow(
        tr("دوبار ضربه روی فضای خالی", "Double tap on empty space"),
        listOf(tr("هیچ", "Nothing"), tr("تنظیمات F OS", "F OS settings"), tr("کشوی برنامه‌ها", "App drawer")), s.doubleTap
    ) { s.doubleTap = it }
    SwitchRow(tr("لرزش هنگام نگه داشتن", "Haptic feedback"), s.haptic) { s.haptic = it }
    SliderRow(tr("سرعت انیمیشن", "Animation speed"), s.animSpeed, 0.5f..2f, label = { String.format(Locale.US, "%.1fx", it) }) { s.animSpeed = it }
    Text(
        tr(
            "کشیدن به بالا: کشوی برنامه‌ها • نگه داشتن روی فضای خالی: منوی سریع",
            "Swipe up: app drawer • Long-press empty space: quick menu"
        ),
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp)
    )
}

@Composable
private fun HiddenPage(vm: LauncherViewModel, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = back), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(tr("برنامه‌های پنهان", "Hidden apps"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(vm.apps.toList(), key = { it.key }) { a ->
                val hiddenNow = a.key in vm.hidden
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleHidden(a.key) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val b = vm.icons[a.key]
                    Box(Modifier.size(40.dp)) { if (b != null) Image(b, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        vm.labelOf(a), Modifier.weight(1f), fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(checked = hiddenNow, onCheckedChange = { vm.toggleHidden(a.key) })
                }
            }
        }
    }
}

@Composable
private fun AboutPage(vm: LauncherViewModel) {
    val s = vm.settings
    ChoiceRow(
        tr("زبان برنامه", "App language"),
        listOf(tr("خودکار", "Auto"), "فارسی", "English"), s.language
    ) { vm.setLanguage(it) }
    ClickRow(
        tr("انتخاب F OS به‌عنوان لانچر پیش‌فرض", "Set F OS as default launcher"),
        if (vm.isDefaultLauncher()) tr("✓ هم‌اکنون لانچر پیش‌فرض است", "✓ Already the default") else null
    ) { vm.openDefaultLauncherSettings() }
    ClickRow(tr("بازنشانی همه تنظیمات و چیدمان", "Reset all settings and layout"), tr("همه چیز به حالت اولیه برمی‌گردد", "Everything returns to defaults")) {
        vm.resetEverything()
        vm.toast(tr("بازنشانی شد", "Reset done"))
    }
    Text(
        "F OS 1.0\n" + tr("لانچر شخصی‌سازی‌پذیر با والپیپر تیلت", "A highly customizable launcher with a tilt wallpaper"),
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp)
    )
}
