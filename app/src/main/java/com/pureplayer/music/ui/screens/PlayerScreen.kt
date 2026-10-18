package com.pureplayer.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pureplayer.music.lyrics.LrcParser
import com.pureplayer.music.lyrics.LyricLine
import com.pureplayer.music.playback.RepeatMode
import com.pureplayer.music.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 全屏播放器：封面/歌词、进度、控制、倍速、睡眠定时 */
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val activity = MainActivity.current()
    val state by activity.player.state.collectAsState()
    val track = state.track

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (track == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有播放歌曲")
                }
            } else {
                PlayerContent(onClose = onClose)
            }
        }
    }
}

@Composable
private fun PlayerContent(onClose: () -> Unit) {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    val state by activity.player.state.collectAsState()
    val track = state.track ?: return

    var showLyrics by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricOffset by remember { mutableStateOf(0L) }

    // 尝试加载同名 .lrc
    LaunchedEffect(track.uri) {
        lyrics = withContext(Dispatchers.IO) {
            try {
                val lrcUri = track.uri.toString()
                    .substringBeforeLast('.') + ".lrc"
                // 只处理 file scheme 的同名 lrc
                if (!lrcUri.startsWith("file")) return@withContext emptyList()
                activity.contentResolver.openInputStream(android.net.Uri.parse(lrcUri))?.use {
                    LrcParser.parse(it.bufferedReader().readText())
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        lyricOffset = 0L
    }

    val lrcPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = activity.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: return@launch
                    lyrics = LrcParser.parse(text)
                } catch (_: Exception) {
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, "收起")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showLyrics = !showLyrics }) {
                Icon(
                    Icons.Filled.Lyrics,
                    "歌词",
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 封面 / 歌词区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (showLyrics) {
                LyricsView(
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                    onImport = { lrcPicker.launch(arrayOf("*/*")) },
                    onOffsetChange = { delta ->
                        lyricOffset += delta
                        lyrics = LrcParser.offset(lyrics, delta)
                    }
                )
            } else {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        // 标题
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            "${track.artist} · ${track.album}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        // 进度条
        var sliderPos by remember { mutableStateOf<Float?>(null) }
        val progress = sliderPos
            ?: if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { sliderPos = it },
            onValueChangeFinished = {
                sliderPos?.let {
                    activity.player.seekTo((it * state.durationMs).toLong())
                }
                sliderPos = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatMs(sliderPos?.let { (it * state.durationMs).toLong() } ?: state.positionMs),
                style = MaterialTheme.typography.bodySmall
            )
            Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        // 主控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { activity.player.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    "随机",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { activity.player.seekTo((state.positionMs - 10000).coerceAtLeast(0)) }) {
                Icon(Icons.Filled.Replay10, "后退 10 秒")
            }
            IconButton(onClick = { activity.player.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "上一首", modifier = Modifier.size(40.dp))
            }
            FilledIconButton(
                onClick = { activity.player.toggle() },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { activity.player.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "下一首", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { activity.player.seekTo(state.positionMs + 10000) }) {
                Icon(Icons.Filled.Forward10, "快进 10 秒")
            }
            IconButton(onClick = { activity.player.toggleRepeat() }) {
                val (icon, tint) = when (state.repeatMode) {
                    RepeatMode.OFF -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
                    RepeatMode.ALL -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                    RepeatMode.ONE -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
                }
                Icon(icon, "循环", tint = tint)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 次控制：倍速 / 睡眠定时
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { showSpeed = true }) {
                Icon(Icons.Filled.Speed, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${state.speed}x")
            }
            TextButton(onClick = { showSleep = true }) {
                Icon(Icons.Filled.Bedtime, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        state.sleepTimerEndAt == -1L -> "播完停止"
                        state.sleepTimerEndAt > 0 -> {
                            val left = (state.sleepTimerEndAt -
                                android.os.SystemClock.elapsedRealtime()) / 60000
                            "剩余 ${left} 分钟"
                        }
                        else -> "睡眠定时"
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // 倍速弹窗（原付费功能）
    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("倍速播放") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activity.player.setSpeed(s)
                                    showSpeed = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.speed == s,
                                onClick = {
                                    activity.player.setSpeed(s)
                                    showSpeed = false
                                }
                            )
                            Text("${s}x")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 睡眠定时弹窗
    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    val options = listOf(
                        0L to "关闭",
                        10L to "10 分钟",
                        20L to "20 分钟",
                        30L to "30 分钟",
                        60L to "60 分钟",
                        61L to "播完当前歌曲"
                    )
                    options.forEach { (min, label) ->
                        val selected = when {
                            min == 0L -> state.sleepTimerEndAt == 0L
                            min == 61L -> state.sleepTimerEndAt == -1L
                            else -> state.sleepTimerEndAt > 0 &&
                                (state.sleepTimerEndAt - android.os.SystemClock.elapsedRealtime()) in
                                ((min - 5) * 60000)..(min * 60000)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activity.player.setSleepTimer(min)
                                    showSleep = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = {
                                activity.player.setSleepTimer(min)
                                showSleep = false
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

/** 歌词视图 */
@Composable
private fun LyricsView(
    lyrics: List<LyricLine>,
    positionMs: Long,
    onImport: () -> Unit,
    onOffsetChange: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val current = LrcParser.indexAt(lyrics, positionMs)

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        if (lyrics.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onImport) { Text("导入歌词文件") }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 32.dp)
            ) {
                itemsIndexed(lyrics) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        fontSize = if (i == current) 20.sp else 15.sp,
                        color = if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 时间线微调
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onImport() }) { Text("重新导入") }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onOffsetChange(-500) }) {
                    Icon(Icons.Filled.Remove, "歌词提前 0.5s")
                }
                Text("时间线微调", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { onOffsetChange(500) }) {
                    Icon(Icons.Filled.Add, "歌词延后 0.5s")
                }
            }
        }
    }
}
