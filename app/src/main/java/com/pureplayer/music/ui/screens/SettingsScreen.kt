package com.pureplayer.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pureplayer.music.BuildConfig
import com.pureplayer.music.ui.MainActivity

/** 设置页 */
@Composable
fun SettingsScreen(nav: NavController) {
    val activity = MainActivity.current()

    Column(Modifier.fillMaxSize()) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        ListItem(
            headlineContent = { Text("均衡器") },
            supportingContent = { Text("调整各频段增益") },
            leadingContent = { Icon(Icons.Filled.Equalizer, null) },
            trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
            modifier = Modifier.clickable { nav.navigate("equalizer") }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("重新扫描音乐库") },
            supportingContent = { Text("扫描设备上的音频文件") },
            leadingContent = { Icon(Icons.Filled.Refresh, null) },
            modifier = Modifier.clickable { activity.scanLibrary() }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("清空播放记录") },
            leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
            modifier = Modifier.clickable {
                // 简单起见用协程
                kotlinx.coroutines.MainScope().launch {
                    activity.db().playRecordDao().clear()
                }
            }
        )
        HorizontalDivider()

        Spacer(Modifier.weight(1f))

        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = { Text("PurePlayer ${BuildConfig.VERSION_NAME}\n本地音乐播放器 · 无账号 · 无广告 · 全功能免费") },
            leadingContent = { Icon(Icons.Filled.Info, null) }
        )
    }
}
