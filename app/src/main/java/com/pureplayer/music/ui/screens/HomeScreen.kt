package com.pureplayer.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pureplayer.music.data.Track
import com.pureplayer.music.data.db.PlayRecord
import com.pureplayer.music.ui.MainActivity
import com.pureplayer.music.ui.db
import kotlinx.coroutines.launch

/** 首页：最近播放 + 推荐 */
@Composable
fun HomeScreen() {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    var recent by remember { mutableStateOf<List<PlayRecord>>(emptyList()) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(Unit) {
        recent = activity.db().playRecordDao().recent()
    }

    // 把播放记录映射回 Track（尽量从当前曲库匹配）
    val tracks = activity.tracks
    fun toTrack(r: PlayRecord): Track? =
        tracks.find { it.uri.toString() == r.trackUri }
            ?: Track(
                id = r.trackUri.hashCode().toLong(),
                title = r.title,
                artist = r.artist,
                album = "",
                albumId = -1,
                durationMs = 0,
                uri = android.net.Uri.parse(r.trackUri)
            )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("首页", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { activity.scanLibrary() }) {
                    Icon(Icons.Filled.Refresh, "重新扫描")
                }
            }
        }

        if (!activity.hasPermission) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "需要存储权限才能扫描本地音乐",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        item {
            Text(
                "最近播放",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (recent.isEmpty()) {
            item {
                Text(
                    "还没有播放记录，去音乐库选一首吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            items(recent.take(20), key = { it.trackUri }) { r ->
                val t = toTrack(r) ?: return@items
                TrackRow(
                    track = t,
                    onClick = { activity.player.play(t, tracks.ifEmpty { listOf(t) }) },
                    onMore = { menuTrack = t }
                )
            }
        }

        item {
            Text(
                "全部音乐（${tracks.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(tracks.take(50), key = { it.uri.toString() }) { t ->
            val state by activity.player.state.collectAsState()
            TrackRow(
                track = t,
                isPlaying = state.track?.uri == t.uri,
                onClick = { activity.player.play(t, tracks) },
                onMore = { menuTrack = t }
            )
        }
    }

    menuTrack?.let { t ->
        AlertDialog(
            onDismissRequest = { menuTrack = null },
            confirmButton = {},
            text = { TrackActionsMenu(track = t, onDismiss = { menuTrack = null }) }
        )
    }
}
