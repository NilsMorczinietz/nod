package io.github.nilsmorczinietz.nod.ui.components

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType
import java.util.Date
import kotlinx.coroutines.delay

/** Current time, ticking every [periodMs] while composed (aligned to the period). */
@Composable
fun rememberNow(periodMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Pushary's "auto-deny 30s" line: "expires in 1:32" in 11 sp tabular, waiting colour. */
@Composable
fun ExpiryText(expiresAt: Long, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val seconds = ((expiresAt - now) / 1000).coerceAtLeast(0)
    Text(
        stringResource(R.string.expires_in, "%d:%02d".format(seconds / 60, seconds % 60)),
        modifier = modifier,
        style = NodType.countdown,
        color = NodTheme.colors.waitingText,
    )
}

/** Short relative time: "now", "4m", "2h", or a date. */
@Composable
fun relativeTime(epochMs: Long): String {
    val now = rememberNow(30_000L)
    val minutes = (now - epochMs) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> DateUtils.formatDateTime(LocalContext.current, epochMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/** Compact clock time, "14:02" (12 h clocks drop AM/PM). */
@Composable
fun ClockTime(epochMs: Long, modifier: Modifier = Modifier, color: Color = NodTheme.colors.mutedForeground) {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    Text(DateFormat.format(pattern, Date(epochMs)).toString(), modifier = modifier, style = NodType.mono, color = color)
}


