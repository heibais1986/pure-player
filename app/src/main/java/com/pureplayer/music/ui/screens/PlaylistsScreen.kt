package com.pureplayer.music.ui.screens

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pureplayer.music.data.Track
import com.pureplayer.music.data.db.Playlist
import com.pureplayer.music.ui.MainActivity
import com.pureplayer.music.ui.db
import kotlinx.coroutines.launch

/** 歌单列表 + 喜欢 */
@Composable
fun PlaylistsScreen(nav: NavController) {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var favCount by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            playlists = activity.db().playlistDao().all()
            favCount = activity.db().favoriteDao().all().size
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, "新建歌单")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Text(
                    "歌单",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("喜欢") },
                    supportingContent = { Text("$favCount 首") },
                    leadingContent = {
                        Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { nav.navigate("playlist/fav") }
                )
            }
            items(playlists, key = { it.id }) { p ->
                var count by remember { mutableIntStateOf(0) }
                LaunchedEffect(p.id) {
                    count = activity.db().playlistDao().count(p.id)
                }
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("$count 首") },
                    leadingContent = { Icon(Icons.Filled.QueueMusic, null) },
                    trailingContent = {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "更多")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                var renaming by remember { mutableStateOf(false) }
                                var renameText by remember { mutableStateOf(p.name) }
                                if (!renaming) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = { renaming = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            showMenu = false
                                            scope.launch {
                                                activity.db().playlistDao().delete(p.id)
                                                reload()
                                            }
                                        }
                                    )
                                } else {
                                    // 简化：直接在菜单里重命名
                                    DropdownMenuItem(
                                        text = {
                                            OutlinedTextField(
                                                value = renameText,
                                                onValueChange = { renameText = it },
                                                singleLine = true
                                            )
                                        },
                                        onClick = {}
                                    )
                                    DropdownMenuItem(
                                        text = { Text("确定") },
                                        onClick = {
                                            showMenu = false
                                            scope.launch {
                                                activity.db().playlistDao()
                                                    .rename(p.id, renameText.ifBlank { p.name })
                                                reload()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { nav.navigate("playlist/${p.id}") }
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌单名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch {
                            activity.db().playlistDao()
                                .insert(Playlist(name = newName.trim()))
                            reload()
                        }
                    }
                    newName = ""
                    showCreate = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
    }
}

/** 歌单详情（含"喜欢"这个特殊歌单） */
@Composable
fun PlaylistDetailScreen(playlistId: Long) {
    val activity = MainActivity.current()
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var title by remember { mutableStateOf("") }

    fun toTrack(uri: String, t: String, a: String, d: Long): Track = Track(
        id = uri.hashCode().toLong(),
        title = t,
        artist = a,
        album = "",
        albumId = -1,
        durationMs = d,
        uri = Uri.parse(uri)
    )

    LaunchedEffect(playlistId) {
        if (playlistId == 0L) {
            // 喜欢
            title = "喜欢"
            val favs = activity.db().favoriteDao().all()
            // 尽量从曲库匹配完整信息
            val lib = activity.tracks.associateBy { it.uri.toString() }
            tracks = favs.map { f ->
                lib[f.trackUri] ?: toTrack(f.trackUri, f.title, f.artist, 0)
            }
        } else {
            val all = activity.db().playlistDao().all()
            title = all.find { it.id == playlistId }?.name ?: "歌单"
            val pts = activity.db().playlistDao().tracks(playlistId).distinctBy { it.trackUri }
            val lib = activity.tracks.associateBy { it.uri.toString() }
            tracks = pts.map { pt ->
                lib[pt.trackUri] ?: toTrack(pt.trackUri, pt.title, pt.artist, pt.durationMs)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                Button(onClick = { activity.player.play(tracks[0], tracks) }) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(tracks, key = { it.uri.toString() }) { t ->
                val state by activity.player.state.collectAsState()
                TrackRow(
                    track = t,
                    isPlaying = state.track?.uri == t.uri,
                    onClick = { activity.player.play(t, tracks) },
                    onMore = {
                        scope.launch {
                            if (playlistId == 0L) {
                                activity.db().favoriteDao().remove(t.uri.toString())
                            } else {
                                val pts = activity.db().playlistDao().tracks(playlistId)
                                pts.find { it.trackUri == t.uri.toString() }?.let {
                                    activity.db().playlistDao().removeTrack(it.id)
                                }
                            }
                            // 刷新
                            val lib = activity.tracks.associateBy { it.uri.toString() }
                            tracks = if (playlistId == 0L) {
                                activity.db().favoriteDao().all().map { f ->
                                    lib[f.trackUri] ?: toTrack(f.trackUri, f.title, f.artist, 0)
                                }
                            } else {
                                activity.db().playlistDao().tracks(playlistId).map { pt ->
                                    lib[pt.trackUri]
                                        ?: toTrack(pt.trackUri, pt.title, pt.artist, pt.durationMs)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
