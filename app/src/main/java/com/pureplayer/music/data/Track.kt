package com.pureplayer.music.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 单曲。uri 可能是 MediaStore content Uri 或文件 Uri。 */
@Parcelize
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val path: String = "",
    val dateAdded: Long = 0L,
    val bitrate: Int = 0
) : Parcelable {
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}
