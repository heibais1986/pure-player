package com.pureplayer.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pureplayer.music.data.Track
import com.pureplayer.music.ui.MainActivity

/** 音乐库：歌曲 / 专辑 / 艺术家 */
@Composable
fun LibraryScreen() {
    val activity = MainActivity.current()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Pair<Track, List<Track>>?>(null) }

    val tracks = activity.tracks
    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索音乐") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            singleLine = true
        )
        TabRow(selectedTabIndex = tab) {
            listOf("歌曲", "专辑", "艺术家").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> SongList(
                tracks = filtered,
                onPlay = { t -> activity.player.play(t, filtered) },
                onMore = { menu = it to filtered }
            )
            1 -> AlbumList(tracks = filtered, onPlay = { t -> activity.player.play(t, filtered) })
            2 -> ArtistList(tracks = filtered, onPlay = { t -> activity.player.play(t, filtered) })
        }
    }

    menu?.let { (t, q) ->
        AlertDialog(
            onDismissRequest = { menu = null },
            confirmButton = {},
            text = {
                TrackActionsMenu(
                    track = t,
                    onDismiss = { menu = null },
                    onShufflePlay = { activity.player.play(t, q, shuffle = true) }
                )
            }
        )
    }
}

@Composable
private fun SongList(
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
    onMore: (Track) -> Unit
) {
    val activity = MainActivity.current()
    val state by activity.player.state.collectAsState()
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(tracks, key = { it.uri.toString() }) { t ->
            TrackRow(
                track = t,
                isPlaying = state.track?.uri == t.uri,
                onClick = { onPlay(t) },
                onMore = { onMore(t) }
            )
        }
    }
}

@Composable
private fun AlbumList(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val activity = MainActivity.current()
    val albums = remember(tracks) { tracks.groupBy { it.album } }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        albums.forEach { (album, songs) ->
            item(key = "album_$album") {
                ListItem(
                    headlineContent = { Text(album) },
                    supportingContent = { Text("${songs.first().artist} · ${songs.size} 首") },
                    leadingContent = {
                        coil.compose.AsyncImage(
                            model = songs.first().albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(songs, key = { it.uri.toString() }) { t ->
                TrackRow(
                    track = t,
                    onClick = { onPlay(t) },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistList(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val artists = remember(tracks) { tracks.groupBy { it.artist } }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        artists.forEach { (artist, songs) ->
            item(key = "artist_$artist") {
                ListItem(
                    headlineContent = { Text(artist) },
                    supportingContent = { Text("${songs.size} 首") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Person,
                            null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
            items(songs, key = { it.uri.toString() }) { t ->
                TrackRow(
                    track = t,
                    onClick = { onPlay(t) },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
