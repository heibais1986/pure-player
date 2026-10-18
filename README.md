# PurePlayer

从 Waca（com.waca.music v1.2.2）逆向提取的**本地音乐播放器**独立 App。

Waca 的本地播放功能全部保留，**原付费功能（均衡器、倍速、±10秒快进）直接内置免费使用**。
去掉了账号系统、后端服务器（newcloud.fun）、友盟统计、Google 计费。

## 功能对照

| 功能 | Waca 原版 | PurePlayer |
|---|---|---|
| 本地音乐扫描/播放 | ✅ 免费 | ✅ |
| 通知栏 + MediaSession 控制 | ✅ 免费 | ✅ |
| 歌单管理（新建/重命名/删除） | ✅ 免费 | ✅ |
| 喜欢 / 最近播放 | ✅ 免费 | ✅ |
| 歌词显示/导入/时间线微调 | ✅ 免费 | ✅ |
| 睡眠定时 | ✅ 免费 | ✅ |
| 断点续播 | ✅ 免费 | ✅ |
| 分享打开音频文件 | ✅ 免费 | ✅ |
| **均衡器** | 💰 付费 | ✅ **免费内置** |
| **倍速 0.5–2x** | 💰 付费 | ✅ **免费内置** |
| **±10 秒快进快退** | 💰 付费 | ✅ **免费内置** |
| 飞牛/Emby/Navidrome 服务器 | ✅ | ❌（仅本地） |
| 账号/云端同步 | ✅ | ❌ |

## 构建

1. 用 Android Studio 打开本目录
2. 等待 Gradle 同步完成
3. Run → 选择设备 → 运行

要求：JDK 17，Android SDK（API 34）。

也可用命令行：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/pureplayer/music/
├── PlayerApplication.kt          # Application（Room 数据库）
├── data/
│   ├── Track.kt                  # 歌曲模型
│   ├── MusicScanner.kt           # MediaStore 本地扫描
│   └── db/Database.kt            # Room：歌单/喜欢/播放记录
├── lyrics/LrcParser.kt           # LRC 解析 + 时间线偏移
├── playback/
│   ├── PlaybackService.kt        # 前台播放服务（MediaPlayer/MediaSession/均衡器/睡眠定时/断点续播）
│   └── PlayerController.kt       # UI ↔ Service 桥梁
└── ui/
    ├── MainActivity.kt           # 入口 + 底部导航
    ├── theme/Theme.kt
    └── screens/
        ├── Common.kt             # 歌曲行、迷你播放器、操作菜单
        ├── HomeScreen.kt         # 首页（最近播放）
        ├── LibraryScreen.kt      # 音乐库（歌曲/专辑/艺术家 + 搜索）
        ├── PlaylistsScreen.kt    # 歌单列表/详情/喜欢
        ├── PlayerScreen.kt       # 全屏播放器（歌词/倍速/睡眠定时）
        ├── EqualizerScreen.kt    # 均衡器
        └── SettingsScreen.kt     # 设置
```

## 权限

- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`（旧版）：扫描本地音乐
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`：后台播放
- `POST_NOTIFICATIONS`：播放通知
- `MODIFY_AUDIO_SETTINGS`：均衡器

无网络权限、无账号、无统计，纯本地。
