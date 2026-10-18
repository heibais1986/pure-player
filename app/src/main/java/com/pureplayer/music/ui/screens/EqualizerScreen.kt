package com.pureplayer.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pureplayer.music.ui.MainActivity

/** 均衡器（Waca 的付费功能，本 App 直接内置免费使用） */
@Composable
fun EqualizerScreen() {
    val activity = MainActivity.current()
    var enabled by remember { mutableStateOf(activity.player.isEqualizerEnabled()) }
    var bands by remember { mutableStateOf(activity.player.equalizerBands()) }
    var levels by remember { mutableStateOf<Map<Int, Short>>(emptyMap()) }

    fun refreshLevels() {
        levels = bands.indices.associateWith { activity.player.getBandLevel(it) }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            bands = activity.player.equalizerBands()
            refreshLevels()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "均衡器",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    activity.player.setEqualizerEnabled(it)
                    if (it) {
                        bands = activity.player.equalizerBands()
                        refreshLevels()
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (!enabled) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "打开开关以启用均衡器",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        if (bands.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "当前设备不支持均衡器，或没有正在播放的音频",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Text(
            "拖动滑杆调整各频段增益",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // 频段滑杆（横向排列）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bands.forEachIndexed { i, (freqKhz, range) ->
                val level: Short = levels[i] ?: 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "%+.1f dB".format(level / 100.0),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    // 竖向滑杆用旋转实现太复杂，这里用横向滑杆纵向排列代替
                    Slider(
                        value = level.toFloat(),
                        onValueChange = {
                            activity.player.setBandLevel(i, it.toInt().toShort())
                            levels = levels + (i to it.toInt().toShort())
                        },
                        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (freqKhz >= 1000) "${freqKhz / 1000}k" else "$freqKhz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                activity.player.resetEqualizer()
                refreshLevels()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("重置")
        }
        Spacer(Modifier.height(16.dp))
    }
}
