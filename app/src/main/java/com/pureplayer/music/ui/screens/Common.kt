package com.pureplayer.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pureplayer.music.data.Track
import com.pureplayer.music.playback.PlayerState
import com.pureplayer.music.ui.MainActivity
import com.pureplayer.music.ui.db
import kotlinx.coroutines.launch

fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** 歌曲行 */
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            fallback = null
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${track.artist} · ${formatMs(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPlaying) {
            Icon(
                Icons.Filled.Equalizer,
                contentDescription = "正在播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, "更多")
            }
        }
    }
}

/** 底部迷你播放器 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val activity = MainActivity.current()
    val state by activity.player.state.collectAsState()
    val track = state.track ?: return

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "暂停" else "播放"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, "下一首")
                }
            }
            // 进度条
            val progress = if (state.durationMs > 0)
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
    }
}

/** 通用歌曲操作菜单：喜欢 / 加到歌单 */
@Composable
fun TrackActionsMenu(
    track: Track,
    onDismiss: () -> Unit,
    onAddedToPlaylist: () -> Unit = {}
) {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    var showPlaylists by remember { mutableStateOf(false) }
    var isFav by remember { mutableStateOf(false) }

    LaunchedEffect(track) {
        isFav = activity.db().favoriteDao().exists(track.uri.toString())
    }

    if (!showPlaylists) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DropdownMenuItem(
                text = { Text(if (isFav) "取消喜欢" else "喜欢") },
                leadingIcon = {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null
                    )
                },
                onClick = {
                    scope.launch {
                        val dao = activity.db().favoriteDao()
                        if (isFav) dao.remove(track.uri.toString())
                        else dao.add(
                            com.pureplayer.music.data.db.Favorite(
                                trackUri = track.uri.toString(),
                                title = track.title,
                                artist = track.artist
                            )
                        )
                        isFav = !isFav
                    }
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = { Text("添加到歌单") },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                onClick = { showPlaylists = true }
            )
        }
    } else {
        AddToPlaylistView(track = track, onDone = {
            onAddedToPlaylist()
            onDismiss()
        })
    }
}

@Composable
private fun AddToPlaylistView(track: Track, onDone: () -> Unit) {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    var playlists by remember {
        mutableStateOf<List<com.pureplayer.music.data.db.Playlist>>(emptyList())
    }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        playlists = activity.db().playlistDao().all()
    }

    Column(Modifier.padding(16.dp)) {
        Text("添加到歌单", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新建歌单") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newName.isBlank()) return@Button
                    scope.launch {
                        val dao = activity.db().playlistDao()
                        val id = dao.insert(
                            com.pureplayer.music.data.db.Playlist(name = newName.trim())
                        )
                        dao.addTrack(
                            com.pureplayer.music.data.db.PlaylistTrack(
                                playlistId = id,
                                trackUri = track.uri.toString(),
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                durationMs = track.durationMs,
                                position = 0
                            )
                        )
                        onDone()
                    }
                }
            ) { Text("创建") }
        }
        Spacer(Modifier.height(8.dp))
        playlists.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name) },
                onClick = {
                    scope.launch {
                        val dao = activity.db().playlistDao()
                        dao.addTrack(
                            com.pureplayer.music.data.db.PlaylistTrack(
                                playlistId = p.id,
                                trackUri = track.uri.toString(),
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                durationMs = track.durationMs,
                                position = dao.count(p.id)
                            )
                        )
                        onDone()
                    }
                }
            )
        }
    }
}
