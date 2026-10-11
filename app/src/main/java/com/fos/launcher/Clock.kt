package com.fos.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.icu.text.SimpleDateFormat as IcuFormat
import android.icu.util.ULocale
import java.util.Date

private fun fmt(pattern: String, locale: String, t: Long): String =
    IcuFormat(pattern, ULocale(locale)).format(Date(t))

@Composable
fun ClockWidget(vm: LauncherViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }

    val fa = Lang.fa
    val tLocale = if (fa) "fa_IR" else "en_US"
    val dLocale = if (s.jalali) {
        if (fa) "fa_IR@calendar=persian" else "en_US@calendar=persian"
    } else {
        if (fa) "fa_IR@calendar=gregorian" else "en_US"
    }
    val hPattern = if (s.clock24) "HH" else "h"
    val time = fmt("$hPattern:mm", tLocale, now)
    val hour = fmt(hPattern, tLocale, now)
    val minute = fmt("mm", tLocale, now)
    val date = fmt("EEEE d MMMM", dLocale, now)

    val shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 10f)
    val big = s.clockSize
    val horiz = if (s.clockAlign == 1) Alignment.CenterHorizontally else Alignment.Start
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.openClockApp() }
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalAlignment = horiz
    ) {
        when (s.clockStyle) {
            1 -> {
                Text(
                    text = if (s.showDate) "$time  ·  $date" else time,
                    color = Color.White,
                    fontSize = (big * 0.42f).sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = shadow)
                )
            }
            2 -> {
                Text(
                    hour, color = Color.White, fontSize = big.sp, fontWeight = FontWeight.Bold,
                    lineHeight = (big * 0.95f).sp, style = TextStyle(shadow = shadow)
                )
                Text(
                    minute, color = accent, fontSize = big.sp, fontWeight = FontWeight.Bold,
                    lineHeight = (big * 0.95f).sp, style = TextStyle(shadow = shadow)
                )
                if (s.showDate) Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, style = TextStyle(shadow = shadow))
            }
            else -> {
                Text(
                    time, color = Color.White, fontSize = big.sp, fontWeight = FontWeight.Light,
                    style = TextStyle(shadow = shadow)
                )
                if (s.showDate) Text(
                    date, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, style = TextStyle(shadow = shadow)
                )
            }
        }
    }
}
