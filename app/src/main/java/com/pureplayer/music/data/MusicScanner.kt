package com.pureplayer.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** 扫描设备本地音频（对标 Waca 的"扫描本地"）。 */
object MusicScanner {

    fun scan(context: Context): List<Track> {
        val result = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        // 过滤掉太短的（<30s 多为通知音/录音片段）
        val selection = "${MediaStore.Audio.Media.DURATION} >= ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val args = arrayOf("30000")
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(
                    Track(
                        id = id,
                        title = c.getString(titleCol) ?: "未知标题",
                        artist = (c.getString(artistCol) ?: "未知艺术家").takeIf { it != "<unknown>" } ?: "未知艺术家",
                        album = (c.getString(albumCol) ?: "未知专辑").takeIf { it != "<unknown>" } ?: "未知专辑",
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durCol),
                        uri = uri,
                        path = c.getString(dataCol) ?: "",
                        dateAdded = c.getLong(addedCol)
                    )
                )
            }
        }
        return result
    }

    /** 从外部分享/VIEW intent 导入单条音频。 */
    fun fromUri(context: Context, uri: Uri): Track? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
                ),
                null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return null
                Track(
                    id = System.currentTimeMillis(),
                    title = c.getString(1) ?: uri.lastPathSegment ?: "未知标题",
                    artist = c.getString(2) ?: "未知艺术家",
                    album = c.getString(3) ?: "未知专辑",
                    albumId = -1,
                    durationMs = try { c.getLong(4) } catch (_: Exception) { 0L },
                    uri = uri
                )
            }
        } catch (_: Exception) {
            Track(
                id = System.currentTimeMillis(),
                title = uri.lastPathSegment ?: "未知标题",
                artist = "未知艺术家",
                album = "未知专辑",
                albumId = -1,
                durationMs = 0L,
                uri = uri
            )
        }
    }
}
