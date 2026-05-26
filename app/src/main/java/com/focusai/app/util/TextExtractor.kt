package com.focusai.app.util

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * 当前屏幕场景：
 * - WATCHING: 检测到视频播放控件 / 时长进度 / 弹幕等强特征 → 用户正在观看
 * - BROWSING: 没有这些特征 → 用户在浏览首页 / 搜索 / 推荐列表
 */
enum class ScreenScene { WATCHING, BROWSING }

/**
 * 一次完整抓取的产物。
 *
 * @property promptText 已清洗 + 标签置顶后的最终文本，可直接送给 AI。
 * @property scene 当前场景，用于在 AI 提示词里加 "[当前场景: ...]" 提示，避免主页误判。
 * @property prominentTags 在 ViewTree 中处于显著位置的短词（典型如分区标签、视频类别）。
 */
data class ScreenSnapshot(
    val promptText: String,
    val scene: ScreenScene,
    val prominentTags: List<String>,
    val primaryTitle: String?
)

object TextExtractor {

    /**
     * 旧 API：仅做 DFS 文本拼接，保持向后兼容。
     */
    fun extractVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val snapshot = extractSnapshot(root)
        return snapshot.promptText
    }

    /**
     * 新 API：从无障碍 ViewTree 抓取一份结构化的屏幕快照。
     *
     * 内部流程：
     *  1. 迭代式 DFS 遍历，避免深层 UI 栈溢出。
     *  2. 同时收集：
     *      - allLines：节点的 text / contentDescription（按出现顺序）
     *      - shortTags：靠前出现的 2~6 字短词，通常是分区/标签
     *      - sceneFeatures：用于判定 WATCHING vs BROWSING 的特征命中
     *  3. 清洗、去重、长度截断
     *  4. 将 shortTags 拼到最前面，使 AI 第一眼就看到「鬼畜·调教」这种关键信号
     */
    fun extractSnapshot(root: AccessibilityNodeInfo): ScreenSnapshot {
        val allLines = ArrayList<String>(128)
        val shortTags = LinkedHashSet<String>(16)
        var primaryTitleCandidate: String? = null
        var watchingHits = 0

        val stack: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        stack.addLast(root)
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++

            if (!node.isVisibleToUser) {
                if (node !== root) runCatching { node.recycle() }
                continue
            }

            val className = node.className?.toString().orEmpty()
            if (WATCHING_CLASS_KEYWORDS.any { className.contains(it, ignoreCase = true) }) {
                watchingHits++
            }

            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
                allLines.add(txt)
                if (isTitleCandidate(txt, primaryTitleCandidate)) {
                    primaryTitleCandidate = txt
                }
                if (txt.length in 2..6 && txt.any { it.isLetter() } && shortTags.size < MAX_TAGS) {
                    shortTags.add(txt)
                }
                if (WATCHING_TEXT_KEYWORDS.any { txt.contains(it) } || TIME_CODE.matches(txt)) {
                    watchingHits++
                }
            }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
                allLines.add(desc)
                if (WATCHING_TEXT_KEYWORDS.any { desc.contains(it) }) watchingHits++
            }

            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }

            if (node !== root) runCatching { node.recycle() }
        }

        val cleanedLines = cleanseLines(allLines)
        val tagHeader = if (shortTags.isNotEmpty()) {
            "【可见标签】" + shortTags.joinToString(" / ")
        } else {
            null
        }

        val titleHeader = primaryTitleCandidate?.let { "【视频主标题】$it" }
        val bodyText = cleanedLines.joinToString("\n").take(MAX_TEXT_LENGTH)
        val finalText = listOfNotNull(titleHeader, tagHeader, bodyText)
            .joinToString("\n")
            .take(MAX_TEXT_LENGTH)

        val scene = if (watchingHits >= WATCHING_THRESHOLD) {
            ScreenScene.WATCHING
        } else {
            ScreenScene.BROWSING
        }

        return ScreenSnapshot(
            promptText = finalText,
            scene = scene,
            prominentTags = shortTags.toList(),
            primaryTitle = primaryTitleCandidate
        )
    }

    /**
     * 数据清洗规则（按用户要求）：
     *  - 去空白
     *  - 去单字符
     *  - 去纯数字 / 纯标点（保留至少一个汉字或字母）
     *  - 顺序去重
     */
    private fun cleanseLines(raw: List<String>): List<String> {
        if (raw.isEmpty()) return emptyList()
        val seen = HashSet<String>(raw.size)
        val out = ArrayList<String>(raw.size)
        for (line in raw) {
            val trimmed = line.trim()
            if (trimmed.length < 2) continue
            if (trimmed.none { it.isLetter() }) continue
            if (seen.add(trimmed)) out.add(trimmed)
        }
        return out
    }

    private fun isTitleCandidate(text: String, current: String?): Boolean {
        val trimmed = text.trim()
        if (trimmed.length !in 8..60) return false
        if (trimmed.none { it.isLetter() }) return false
        if (WATCHING_TEXT_KEYWORDS.any { trimmed.contains(it) }) return false
        if (TIME_CODE.matches(trimmed)) return false
        return current == null || trimmed.length > current.length
    }

    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────── 内部常量 ───────────────────────

    private const val MAX_NODES = 1_500
    private const val MAX_TEXT_LENGTH = 3_500
    private const val MAX_TAGS = 12

    /** 命中 >= 此阈值时认为是"播放中"。 */
    private const val WATCHING_THRESHOLD = 2

    /** 节点 className 中含有这些关键词 → 强烈暗示「视频播放」。 */
    private val WATCHING_CLASS_KEYWORDS = listOf(
        "SeekBar", "VideoView", "SurfaceView", "PlayerView",
        "ExoPlayer", "TextureView"
    )

    /** 节点文本/描述中出现这些关键词 → 暗示「视频播放」。 */
    private val WATCHING_TEXT_KEYWORDS = listOf(
        "弹幕", "暂停", "播放", "全屏", "倍速", "继续观看",
        "Pause", "Play", "Fullscreen"
    )

    /** 时间码格式（例如 "12:34" 或 "01:23:45"），强烈暗示视频播放控件存在。 */
    private val TIME_CODE = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
}
