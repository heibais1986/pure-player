package com.pureplayer.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.pureplayer.music.R
import com.pureplayer.music.data.Track
import com.pureplayer.music.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** 播放状态（UI 观察用） */
data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val sleepTimerMs: Long = 0L,
    val sleepTimerEndAt: Long = 0L
)

enum class RepeatMode { OFF, ONE, ALL }

/**
 * 前台播放服务。对标 Waca PlaybackService 的本地播放部分：
 * 免费功能：播放/暂停/上下首、通知栏控制、MediaSession、睡眠定时、断点续播
 * 原付费功能（本 App 直接内置）：均衡器、0.5–2x 倍速
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.pureplayer.music.TOGGLE"
        const val ACTION_NEXT = "com.pureplayer.music.NEXT"
        const val ACTION_PREV = "com.pureplayer.music.PREV"
        const val ACTION_PLAY_TRACK = "com.pureplayer.music.PLAY_TRACK"
        const val ACTION_SEEK = "com.pureplayer.music.SEEK"
        const val ACTION_SPEED = "com.pureplayer.music.SPEED"
        const val ACTION_SLEEP = "com.pureplayer.music.SLEEP_TIMER"
        const val ACTION_RESTORE = "com.pureplayer.music.RESTORE"
        const val NOTIF_ID = 520
        const val CHANNEL_ID = "playback"

        private val _state = MutableStateFlow(PlayerState())
        val state: StateFlow<PlayerState> = _state
    }

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var equalizer: Equalizer? = null

    private var queue: List<Track> = emptyList()
    private var index: Int = -1
    private var shuffledOrder: List<Int> = emptyList()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            updatePosition()
            checkSleepTimer()
            handler.postDelayed(this, 500)
        }
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("player_settings", MODE_PRIVATE)
    }

    inner class LocalBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "正在播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示当前歌曲和播放控制"
                setShowBadge(false)
            }
        )
        mediaSession = MediaSession(this, "PurePlayer").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        // 恢复均衡器设置
        if (prefs.getBoolean("equalizer_enabled", false)) {
            applyEqualizer()
        }
        handler.post(progressTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTick)
        scope.cancel()
        releasePlayer()
        abandonFocus()
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_PLAY_TRACK -> {
                @Suppress("DEPRECATION")
                val track: Track? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra("track", Track::class.java)
                else intent.getParcelableExtra("track")
                @Suppress("UNCHECKED_CAST")
                val q: ArrayList<Track>? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableArrayListExtra("queue", Track::class.java)
                else intent.getParcelableArrayListExtra("queue")
                if (track != null) play(track, q ?: listOf(track))
            }
            ACTION_SEEK -> seekTo(intent.getLongExtra("position", 0L))
            ACTION_SPEED -> setSpeed(intent.getFloatExtra("speed", 1f))
            ACTION_SLEEP -> setSleepTimer(intent.getLongExtra("minutes", 0L))
            ACTION_RESTORE -> restoreLast()
        }
        return START_STICKY
    }

    // ---------- 播放控制 ----------

    fun play(track: Track, newQueue: List<Track>) {
        queue = newQueue
        index = newQueue.indexOfFirst { it.uri == track.uri }.takeIf { it >= 0 } ?: 0
        shuffledOrder = queue.indices.shuffled()
        startTrack(queue[index], 0L)
    }

    fun toggle() {
        val mp = mediaPlayer
        if (mp == null) {
            val t = _state.value.track
            if (t != null) play(t, queue.ifEmpty { listOf(t) })
            return
        }
        if (mp.isPlaying) pause() else resume()
    }

    fun next() {
        if (queue.isEmpty()) return
        index = nextIndex()
        startTrack(queue[index], 0L)
    }

    fun previous() {
        val mp = mediaPlayer
        // 播放超过 4 秒则回到开头，否则上一首（对标主流播放器行为）
        if (mp != null && mp.currentPosition > 4000) {
            seekTo(0)
            return
        }
        if (queue.isEmpty()) return
        index = if (_state.value.shuffle) {
            val pos = shuffledOrder.indexOf(index)
            shuffledOrder[(pos - 1 + shuffledOrder.size) % shuffledOrder.size]
        } else {
            (index - 1 + queue.size) % queue.size
        }
        startTrack(queue[index], 0L)
    }

    fun seekTo(ms: Long) {
        try {
            mediaPlayer?.seekTo(ms.toInt())
            updatePosition()
        } catch (_: Exception) {
        }
    }

    /** 倍速 0.5–2x（原付费功能，本 App 直接内置） */
    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        try {
            mediaPlayer?.let {
                it.playbackParams = it.playbackParams.setSpeed(s)
            }
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(speed = s)
        prefs.edit().putFloat("speed", s).apply()
        updateSession()
    }

    fun toggleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = next)
    }

    fun toggleShuffle() {
        val s = !_state.value.shuffle
        if (s) shuffledOrder = queue.indices.shuffled()
        _state.value = _state.value.copy(shuffle = s)
    }

    /** 睡眠定时（分钟，0=关闭；61=播完当前歌曲） */
    fun setSleepTimer(minutes: Long) {
        val endAt = when {
            minutes <= 0 -> 0L
            minutes >= 61 -> -1L // 播完当前
            else -> SystemClock.elapsedRealtime() + minutes * 60_000
        }
        _state.value = _state.value.copy(
            sleepTimerMs = minutes * 60_000,
            sleepTimerEndAt = endAt
        )
    }

    // ---------- 均衡器（原付费功能，本 App 直接内置） ----------

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("equalizer_enabled", enabled).apply()
        if (enabled) applyEqualizer() else equalizer?.enabled = false
    }

    fun isEqualizerEnabled(): Boolean = prefs.getBoolean("equalizer_enabled", false)

    fun equalizerBands(): List<Pair<Int, ClosedRange<Int>>> {
        val eq = equalizer ?: return emptyList()
        return try {
            val range: ShortArray = eq.bandLevelRange
            val levelRange = range[0].toInt()..range[1].toInt()
            (0 until eq.numberOfBands.toInt()).map { i ->
                eq.getCenterFreq(i.toShort()) / 1000 to levelRange
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getBandLevel(band: Int): Short = try {
        equalizer?.getBandLevel(band.toShort()) ?: 0
    } catch (_: Exception) {
        0
    }

    fun setBandLevel(band: Int, level: Short) {
        try {
            val eq = equalizer ?: return
            val range = eq.bandLevelRange
            eq.setBandLevel(band.toShort(), level.coerceIn(range[0], range[1]))
            prefs.edit().putInt("eq_band_$band", level.toInt()).apply()
        } catch (_: Exception) {
        }
    }

    fun resetEqualizer() {
        val n = try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }
        for (i in 0 until n) setBandLevel(i, 0)
    }

    private fun applyEqualizer() {
        val mp = mediaPlayer ?: return
        try {
            var eq = equalizer
            if (eq == null) {
                eq = Equalizer(0, mp.audioSessionId)
                equalizer = eq
            }
            val range = eq.bandLevelRange
            val n = eq.numberOfBands
            for (i in 0 until n) {
                val saved = prefs.getInt("eq_band_$i", 0)
                eq.setBandLevel(i.toShort(), saved.toShort().coerceIn(range[0], range[1]))
            }
            eq.enabled = true
        } catch (_: Exception) {
        }
    }

    // ---------- 内部 ----------

    private fun nextIndex(): Int {
        val st = _state.value
        return when {
            st.repeatMode == RepeatMode.ONE -> index
            st.shuffle -> {
                val pos = shuffledOrder.indexOf(index)
                shuffledOrder[(pos + 1) % shuffledOrder.size]
            }
            else -> (index + 1) % queue.size
        }
    }

    private fun startTrack(track: Track, startMs: Long) {
        releasePlayer()
        // 记录最近播放
        scope.launch(Dispatchers.IO) {
            try {
                val app = application as com.pureplayer.music.PlayerApplication
                app.db.playRecordDao().record(
                    com.pureplayer.music.data.db.PlayRecord(
                        trackUri = track.uri.toString(),
                        title = track.title,
                        artist = track.artist
                    )
                )
            } catch (_: Exception) {
            }
        }
        if (!requestFocus()) {
            // 无音频焦点则仍尝试播放（部分设备行为）
        }
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(this, track.uri)
            mp.setOnPreparedListener {
                if (isEqualizerEnabled()) applyEqualizer()
                val speed = prefs.getFloat("speed", 1f)
                if (speed != 1f) {
                    try {
                        it.playbackParams = it.playbackParams.setSpeed(speed)
                    } catch (_: Exception) {
                    }
                    _state.value = _state.value.copy(speed = speed)
                }
                if (startMs > 0) it.seekTo(startMs.toInt())
                it.start()
                _state.value = _state.value.copy(
                    track = track,
                    isPlaying = true,
                    durationMs = it.duration.toLong()
                )
                startForeground(NOTIF_ID, buildNotification())
                updateSession()
            }
            mp.setOnCompletionListener {
                val st = _state.value
                when {
                    st.sleepTimerEndAt == -1L -> {
                        // 睡眠定时=播完当前
                        pause()
                        setSleepTimer(0)
                    }
                    st.repeatMode == RepeatMode.ONE -> {
                        startTrack(track, 0L)
                    }
                    else -> next()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                _state.value = _state.value.copy(isPlaying = false)
                true
            }
            mp.prepareAsync()
            _state.value = _state.value.copy(track = track, isPlaying = false)
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    private fun resume() {
        if (!requestFocus()) return
        try {
            mediaPlayer?.start()
            _state.value = _state.value.copy(isPlaying = true)
            startForeground(NOTIF_ID, buildNotification())
            updateSession()
        } catch (_: Exception) {
        }
    }

    private fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isPlaying = false)
        // 降为普通通知（可滑动清除）
        stopForeground(STOP_FOREGROUND_DETACH)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
        updateSession()
        saveProgress()
    }

    private fun releasePlayer() {
        saveProgress()
        equalizer?.release()
        equalizer = null
        try {
            mediaPlayer?.let {
                it.setOnPreparedListener(null)
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun updatePosition() {
        val mp = mediaPlayer ?: return
        try {
            _state.value = _state.value.copy(
                positionMs = mp.currentPosition.toLong(),
                durationMs = mp.duration.toLong().takeIf { it > 0 } ?: _state.value.durationMs
            )
        } catch (_: Exception) {
        }
    }

    private fun checkSleepTimer() {
        val st = _state.value
        val endAt = st.sleepTimerEndAt
        if (endAt > 0 && SystemClock.elapsedRealtime() >= endAt && st.isPlaying) {
            pause()
            setSleepTimer(0)
        }
    }

    /** 断点续播：保存 track + 进度 */
    private fun saveProgress() {
        val st = _state.value
        val t = st.track ?: return
        prefs.edit()
            .putString("last_track_uri", t.uri.toString())
            .putString("last_track_title", t.title)
            .putString("last_track_artist", t.artist)
            .putLong("last_position", st.positionMs)
            .apply()
    }

    private fun restoreLast() {
        if (_state.value.track != null) return
        val uriStr = prefs.getString("last_track_uri", null) ?: return
        val track = Track(
            id = uriStr.hashCode().toLong(),
            title = prefs.getString("last_track_title", "未知标题")!!,
            artist = prefs.getString("last_track_artist", "未知艺术家")!!,
            album = "",
            albumId = -1,
            durationMs = 0,
            uri = Uri.parse(uriStr)
        )
        play(track, listOf(track))
        handler.postDelayed({ seekTo(prefs.getLong("last_position", 0L)) }, 800)
    }

    // ---------- 音频焦点 ----------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                try {
                    mediaPlayer?.setVolume(0.25f, 0.25f)
                } catch (_: Exception) {
                }
            AudioManager.AUDIOFOCUS_GAIN ->
                try {
                    mediaPlayer?.setVolume(1f, 1f)
                } catch (_: Exception) {
                }
        }
    }

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= 26) {
            var req = audioFocusRequest
            if (req == null) {
                req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                audioFocusRequest = req
            }
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
    }

    // ---------- 通知栏 / MediaSession ----------

    private fun pendingAction(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val st = _state.value
        val t = st.track
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(t?.title ?: "PurePlayer")
            .setContentText(t?.let { "${it.artist} · ${it.album}" } ?: "本地音乐播放器")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(st.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "上一首", pendingAction(ACTION_PREV, 1))
            .addAction(
                if (st.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (st.isPlaying) "暂停" else "播放",
                pendingAction(ACTION_TOGGLE, 2)
            )
            .addAction(R.drawable.ic_skip_next, "下一首", pendingAction(ACTION_NEXT, 3))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(
                        (mediaSession?.sessionToken as? android.media.session.MediaSession.Token)?.let {
                            android.support.v4.media.session.MediaSessionCompat.Token.fromToken(it)
                        }
                    )
                    .setShowActionsInCompactView(0, 1, 2)
            )
        loadArt(t)?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun loadArt(t: Track?): Bitmap? {
        if (t == null || t.albumId < 0) return null
        return try {
            contentResolver.openInputStream(t.albumArtUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() = resume()
        override fun onPause() = pause()
        override fun onSkipToNext() = next()
        override fun onSkipToPrevious() = previous()
        override fun onSeekTo(pos: Long) = seekTo(pos)
    }

    private fun updateSession() {
        val st = _state.value
        val ms = mediaSession ?: return
        val t = st.track
        if (t != null) {
            ms.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, max(st.durationMs, 0))
                    .build()
            )
        }
        ms.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (st.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    st.positionMs, st.speed
                )
                .build()
        )
    }
}
