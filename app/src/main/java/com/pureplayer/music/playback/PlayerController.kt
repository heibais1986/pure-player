package com.pureplayer.music.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.pureplayer.music.data.Track
import kotlinx.coroutines.flow.StateFlow

/** UI 层与 PlaybackService 的桥梁：绑定服务 + 转发指令。 */
class PlayerController(private val context: Context) {

    private var service: PlaybackService? = null
    private var bound = false

    val state: StateFlow<PlayerState> = PlaybackService.state

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as PlaybackService.LocalBinder).service()
            bound = true
            pendingPlay?.let { (t, q) ->
                pendingPlay = null
                service?.play(t, q)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private var pendingPlay: Pair<Track, List<Track>>? = null

    fun start() {
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        // 尝试断点续播
        context.startService(intent.setAction(PlaybackService.ACTION_RESTORE))
    }

    fun stop() {
        if (bound) {
            context.unbindService(conn)
            bound = false
        }
    }

    fun play(track: Track, queue: List<Track>) {
        val s = service
        if (s != null) s.play(track, queue) else pendingPlay = track to queue
    }

    fun playUri(uri: Uri) {
        val track = com.pureplayer.music.data.MusicScanner.fromUri(context, uri) ?: return
        play(track, listOf(track))
    }

    fun toggle() = service?.toggle()
        ?: context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_TOGGLE)
        )

    fun next() = service?.next()
        ?: context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_NEXT)
        )

    fun previous() = service?.previous()
        ?: context.startService(
            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_PREV)
        )

    fun seekTo(ms: Long) {
        service?.seekTo(ms) ?: context.startService(
            Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_SEEK)
                .putExtra("position", ms)
        )
    }

    fun setSpeed(speed: Float) {
        service?.setSpeed(speed) ?: context.startService(
            Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_SPEED)
                .putExtra("speed", speed)
        )
    }

    fun toggleRepeat() = service?.toggleRepeat()
    fun toggleShuffle() = service?.toggleShuffle()

    fun setSleepTimer(minutes: Long) {
        service?.setSleepTimer(minutes) ?: context.startService(
            Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_SLEEP)
                .putExtra("minutes", minutes)
        )
    }

    // 均衡器
    fun setEqualizerEnabled(e: Boolean) = service?.setEqualizerEnabled(e)
    fun isEqualizerEnabled(): Boolean = service?.isEqualizerEnabled() ?: false
    fun equalizerBands() = service?.equalizerBands() ?: emptyList()
    fun getBandLevel(b: Int) = service?.getBandLevel(b) ?: 0
    fun setBandLevel(b: Int, l: Short) = service?.setBandLevel(b, l)
    fun resetEqualizer() = service?.resetEqualizer()
}
