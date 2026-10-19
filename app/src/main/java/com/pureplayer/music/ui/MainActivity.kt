package com.pureplayer.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.pureplayer.music.PlayerApplication
import com.pureplayer.music.data.MusicScanner
import com.pureplayer.music.data.Track
import com.pureplayer.music.playback.PlayerController
import com.pureplayer.music.ui.screens.*
import com.pureplayer.music.ui.theme.PurePlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    lateinit var player: PlayerController
        private set

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var hasPermission by mutableStateOf(false)
        private set

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission = granted.values.any { it }
        if (hasPermission) scanLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = PlayerController(this)
        player.start()

        checkPermission()
        handleIntent(intent)

        setContent {
            PurePlayerTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            lifecycleScope.launch {
                // 等待服务绑定
                withContext(Dispatchers.IO) { Thread.sleep(600) }
                player.playUri(uri)
            }
        }
    }

    private fun checkPermission() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        hasPermission = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            permLauncher.launch(perms)
        } else {
            scanLibrary()
        }
        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    fun scanLibrary() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = try {
                MusicScanner.scan(this@MainActivity)
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) { tracks = list }
        }
    }

    @Composable
    fun AppNav() {
        val nav = rememberNavController()
        var showPlayer by remember { mutableStateOf(false) }

        data class Tab(val route: String, val label: String, val icon: ImageVector)
        val tabs = listOf(
            Tab("home", "首页", Icons.Filled.Home),
            Tab("library", "音乐库", Icons.Filled.LibraryMusic),
            Tab("playlists", "歌单", Icons.Filled.QueueMusic),
            Tab("settings", "设置", Icons.Filled.Settings),
        )

        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(
                        onOpen = { showPlayer = true },
                        onToggle = { player.toggle() },
                        onNext = { player.next() }
                    )
                    NavigationBar {
                        val backStack by nav.currentBackStackEntryAsState()
                        val current = backStack?.destination?.route
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = current == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") { HomeScreen() }
                composable("library") { LibraryScreen() }
                composable("playlists") { PlaylistsScreen(nav) }
                composable("playlist/{id}") { backStack ->
                    PlaylistDetailScreen(backStack.arguments?.getString("id")?.toLongOrNull() ?: 0L)
                }
                composable("settings") { SettingsScreen(nav) }
                composable("equalizer") { EqualizerScreen() }
            }
        }

        if (showPlayer) {
            PlayerScreen(onClose = { showPlayer = false })
        }
    }

    // 让子 Composable 能拿到 Activity
    companion object {
        @Composable
        fun current(): MainActivity {
            // AlertDialog / Popup 里 LocalContext 是 ContextThemeWrapper，直接强转会 ClassCastException
            var ctx: android.content.Context =
                androidx.compose.ui.platform.LocalContext.current
            while (ctx is android.content.ContextWrapper) {
                if (ctx is MainActivity) return ctx
                ctx = ctx.baseContext
            }
            error("LocalContext 中没有 MainActivity")
        }
    }
}

fun MainActivity.db() = (application as PlayerApplication).db
