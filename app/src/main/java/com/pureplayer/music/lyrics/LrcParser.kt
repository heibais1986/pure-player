package com.pureplayer.music.lyrics

/** 一行歌词 */
data class LyricLine(val timeMs: Long, val text: String)

/** LRC 歌词解析（对标 Waca 的歌词导入/编辑）。支持 [mm:ss.xx] 时间标签。 */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) continue
            val textPart = line.replace(timeTag, "").trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    3 -> frac.toLong()
                    2 -> frac.toLong() * 10
                    1 -> frac.toLong() * 100
                    else -> 0L
                }
                lines.add(LyricLine(min * 60_000 + sec * 1000 + ms, textPart))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 根据播放进度找当前行下标 */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        return idx
    }

    /** 时间线整体偏移（毫秒），对标 Waca 的"歌词时间线"调整 */
    fun offset(lines: List<LyricLine>, offsetMs: Long): List<LyricLine> =
        lines.map { it.copy(timeMs = maxOf(0, it.timeMs + offsetMs)) }
}
