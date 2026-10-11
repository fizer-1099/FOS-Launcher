package com.fos.launcher

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

val WallpaperPresets: List<List<Color>> = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF2B1055), Color(0xFF7597DE), Color(0xFFD16BA5)),
    listOf(Color(0xFF42275A), Color(0xFF734B6D), Color(0xFFE7A977)),
    listOf(Color(0xFF000000), Color(0xFF1A1A2E), Color(0xFF16213E)),
    listOf(Color(0xFF134E5E), Color(0xFF3AA17E), Color(0xFF71B280)),
    listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
    listOf(Color(0xFF0B486B), Color(0xFF3B8686), Color(0xFFCFF09E)),
    listOf(Color(0xFF232526), Color(0xFF414345), Color(0xFF8E9EAB))
)

val WallpaperPresetNames: List<Pair<String, String>> = listOf(
    "اقیانوس" to "Ocean", "شفق" to "Aurora", "غروب" to "Sunset", "نیمه‌شب" to "Midnight",
    "جنگل" to "Forest", "آتش" to "Ember", "دریاچه" to "Lagoon", "فولاد" to "Steel"
)

@Composable
private fun WallpaperSource(vm: LauncherViewModel, blurred: Boolean, modifier: Modifier) {
    val s = vm.settings
    val img = vm.wpImage
    if (s.wpMode == 1 && img != null) {
        Image(
            bitmap = if (blurred) (vm.wpBlurImage ?: img) else img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = modifier
        )
    } else {
        val colors = WallpaperPresets[s.wpPreset.coerceIn(0, WallpaperPresets.lastIndex)]
        Box(
            modifier
                .background(Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite))
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colors.last().copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.8f, size.height * 0.18f),
                            radius = size.minDimension * 0.9f
                        ),
                        radius = size.minDimension * 0.9f,
                        center = Offset(size.width * 0.8f, size.height * 0.18f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colors.first().copy(alpha = 0.7f), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.85f),
                            radius = size.minDimension * 0.8f
                        ),
                        radius = size.minDimension * 0.8f,
                        center = Offset(size.width * 0.1f, size.height * 0.85f)
                    )
                }
        )
    }
}

/**
 * Solo Tilt style live wallpaper: a blurred backdrop layer, a sharp image layer that
 * moves / rotates in perspective with the phone's tilt, a moving glare, and a dim layer.
 */
@Composable
fun TiltWallpaper(vm: LauncherViewModel, tilt: TiltSensor, modifier: Modifier = Modifier.fillMaxSize()) {
    val s = vm.settings
    Box(modifier.background(Color.Black)) {
        // 1) blurred backdrop – moves the opposite way for depth
        val blurMod = if (Build.VERSION.SDK_INT >= 31 && s.wpMode == 1) Modifier.blur((s.wpBlur * 18f).dp) else Modifier
        WallpaperSource(
            vm, true,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = if (s.tiltOn) 1f else 0f
                    scaleX = 1.4f
                    scaleY = 1.4f
                    translationX = -tilt.x * size.width * 0.04f * s.tiltDepth * k
                    translationY = -tilt.y * size.height * 0.025f * s.tiltDepth * k
                }
                .then(blurMod)
        )
        // 2) sharp main layer with perspective
        WallpaperSource(
            vm, false,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = if (s.tiltOn) 1f else 0f
                    cameraDistance = 14f * density
                    scaleX = s.wpZoom
                    scaleY = s.wpZoom
                    translationX = tilt.x * size.width * 0.10f * s.tiltDepth * k
                    translationY = tilt.y * size.height * 0.06f * s.tiltDepth * k
                    rotationY = tilt.x * s.tiltPersp * k
                    rotationX = -tilt.y * s.tiltPersp * k
                }
        )
        // 3) glare that slides with the tilt
        if (s.tiltGlare && s.tiltOn) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val c = Offset(size.width * (0.5f + tilt.x * 0.6f), size.height * (0.3f - tilt.y * 0.4f))
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                                center = c,
                                radius = size.maxDimension * 0.7f
                            )
                        )
                    }
            )
        }
        // 4) dim
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.wpDim.coerceIn(0f, 0.9f))))
    }
}
