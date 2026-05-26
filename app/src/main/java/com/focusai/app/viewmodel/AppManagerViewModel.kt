package com.focusai.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusai.app.FocusAiApplication
import com.focusai.app.data.prefs.AppCategory
import com.focusai.app.data.prefs.UserAppLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val category: AppCategory
)

data class AppManagerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<InstalledAppItem> = emptyList(),
    val visibleItems: List<InstalledAppItem> = emptyList(),
    val counts: Map<AppCategory, Int> = emptyMap()
)

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusAiApplication
    private val appListRepository = app.appListRepository

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    /** 原始（未应用搜索过滤）的 App 列表。Drawable 不放进 StateFlow 之外的地方避免 leak。 */
    private var allApps: List<InstalledAppItem> = emptyList()

    init {
        loadInstalledApps()

        viewModelScope.launch {
            appListRepository.listsFlow.collect { lists ->
                applyCategoryUpdate(lists)
            }
        }
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value, visibleItems = filter(allApps, value)) }
    }

    fun setCategory(packageName: String, category: AppCategory) {
        viewModelScope.launch {
            appListRepository.setCategory(packageName, category)
        }
    }

    fun reload() {
        loadInstalledApps()
    }

    // ────────────────────────────── 内部 ──────────────────────────────

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }

            val rawApps = withContext(Dispatchers.IO) {
                queryUserInstalledApps(app)
            }

            val currentLists = appListRepository.getSnapshot()
            allApps = rawApps.map { (info, label, icon) ->
                InstalledAppItem(
                    packageName = info.packageName,
                    label = label,
                    icon = icon,
                    category = currentLists.categoryOf(info.packageName)
                )
            }.sortedWith(
                compareBy({ it.category.sortKey() }, { it.label.lowercase() })
            )

            _uiState.update { current ->
                current.copy(
                    loading = false,
                    items = allApps,
                    visibleItems = filter(allApps, current.query),
                    counts = computeCounts(allApps)
                )
            }
        }
    }

    /** 当 DataStore 名单变化时（例如用户切换了某个 App 的归类），只更新内存视图。 */
    private fun applyCategoryUpdate(lists: UserAppLists) {
        if (allApps.isEmpty()) return
        val updated = allApps.map { it.copy(category = lists.categoryOf(it.packageName)) }
            .sortedWith(compareBy({ it.category.sortKey() }, { it.label.lowercase() }))
        allApps = updated
        _uiState.update { current ->
            current.copy(
                items = updated,
                visibleItems = filter(updated, current.query),
                counts = computeCounts(updated)
            )
        }
    }

    private fun filter(items: List<InstalledAppItem>, query: String): List<InstalledAppItem> {
        if (query.isBlank()) return items
        val q = query.trim().lowercase()
        return items.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    private fun computeCounts(items: List<InstalledAppItem>): Map<AppCategory, Int> {
        val map = HashMap<AppCategory, Int>(4)
        AppCategory.entries.forEach { map[it] = 0 }
        items.forEach { map[it.category] = (map[it.category] ?: 0) + 1 }
        return map
    }

    private fun AppCategory.sortKey(): Int = when (this) {
        AppCategory.BLACKLIST -> 0
        AppCategory.GREYLIST -> 1
        AppCategory.WHITELIST -> 2
        AppCategory.UNMANAGED -> 3
    }

    /**
     * 查询用户已安装 App（有 Launcher 图标，排除系统 App）。
     * 图标在 IO 线程一次性加载，避免列表滚动时卡顿。
     */
    private fun queryUserInstalledApps(application: Application): List<Triple<ApplicationInfo, String, Drawable?>> {
        val pm = application.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        val seen = HashSet<String>(resolveInfos.size)
        val result = ArrayList<Triple<ApplicationInfo, String, Drawable?>>(resolveInfos.size)

        for (ri in resolveInfos) {
            val info = ri.activityInfo.applicationInfo
            // 隐藏系统 App + 自己
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            ) continue
            if (info.packageName == application.packageName) continue
            if (!seen.add(info.packageName)) continue

            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
            result.add(Triple(info, label, icon))
        }
        return result
    }
}
