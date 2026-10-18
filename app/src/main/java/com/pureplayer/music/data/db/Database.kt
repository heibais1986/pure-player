package com.pureplayer.music.data.db

import androidx.room.*

/** 歌单 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 歌单-歌曲关联（存 uri 字符串 + 元信息，保证文件移动后仍可显示） */
@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [ForeignKey(
        entity = Playlist::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("playlistId")]
)
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val position: Int
)

/** 喜欢 */
@Entity(tableName = "favorites", primaryKeys = ["trackUri"])
data class Favorite(
    val trackUri: String,
    val title: String,
    val artist: String,
    val addedAt: Long = System.currentTimeMillis()
)

/** 播放记录（最近播放） */
@Entity(tableName = "play_records", primaryKeys = ["trackUri"])
data class PlayRecord(
    val trackUri: String,
    val title: String,
    val artist: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun all(): List<Playlist>

    @Insert
    suspend fun insert(p: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :pid ORDER BY position")
    suspend fun tracks(pid: Long): List<PlaylistTrack>

    @Insert
    suspend fun addTrack(t: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE id = :id")
    suspend fun removeTrack(id: Long)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :pid")
    suspend fun count(pid: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun all(): List<Favorite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: Favorite)

    @Query("DELETE FROM favorites WHERE trackUri = :uri")
    suspend fun remove(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackUri = :uri)")
    suspend fun exists(uri: String): Boolean
}

@Dao
interface PlayRecordDao {
    @Query("SELECT * FROM play_records ORDER BY playedAt DESC LIMIT 100")
    suspend fun recent(): List<PlayRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(r: PlayRecord)

    @Query("DELETE FROM play_records")
    suspend fun clear()
}

@Database(
    entities = [Playlist::class, PlaylistTrack::class, Favorite::class, PlayRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playRecordDao(): PlayRecordDao
}
