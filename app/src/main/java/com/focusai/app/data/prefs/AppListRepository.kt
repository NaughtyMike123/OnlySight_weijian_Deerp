package com.focusai.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 用户对一个 App 的自定义归类。
 * - BLACKLIST: 直接踢回桌面（不调用 AI）
 * - WHITELIST: 完全忽略（不监督）
 * - GREYLIST: 进入 AI 判断（适合图文+视频混合 App）
 * - UNMANAGED: 用户没显式分类，由内置默认 + 启发式决定
 */
enum class AppCategory { BLACKLIST, WHITELIST, GREYLIST, UNMANAGED }

/**
 * 用户三色名单的快照。
 */
data class UserAppLists(
    val blacklist: Set<String> = emptySet(),
    val whitelist: Set<String> = emptySet(),
    val greylist: Set<String> = emptySet()
) {
    /**
     * 查询某个包名的用户归类。返回 [AppCategory.UNMANAGED] 表示用户没设置。
     */
    fun categoryOf(packageName: String): AppCategory = when (packageName) {
        in blacklist -> AppCategory.BLACKLIST
        in whitelist -> AppCategory.WHITELIST
        in greylist -> AppCategory.GREYLIST
        else -> AppCategory.UNMANAGED
    }
}

private val Context.appListDataStore: DataStore<Preferences> by preferencesDataStore(name = "focusai_app_lists")

/**
 * 管理用户自定义的 App 黑/白/灰三色名单。
 * 数据存在独立的 DataStore 里，与 SettingsRepository 分离，避免高频读写互相干扰。
 */
class AppListRepository(private val context: Context) {

    val listsFlow: Flow<UserAppLists> = context.appListDataStore.data.map { prefs ->
        UserAppLists(
            blacklist = prefs[KEY_BLACKLIST] ?: emptySet(),
            whitelist = prefs[KEY_WHITELIST] ?: emptySet(),
            greylist = prefs[KEY_GREYLIST] ?: emptySet()
        )
    }

    suspend fun getSnapshot(): UserAppLists = listsFlow.first()

    /**
     * 把 [packageName] 设置为 [category]。
     * - 内部会从其他名单里移除此包名，保证一个包名只能在一个名单里。
     * - 若 [category] 为 [AppCategory.UNMANAGED]，则从所有名单移除。
     */
    suspend fun setCategory(packageName: String, category: AppCategory) {
        context.appListDataStore.edit { prefs ->
            val black = (prefs[KEY_BLACKLIST] ?: emptySet()).toMutableSet()
            val white = (prefs[KEY_WHITELIST] ?: emptySet()).toMutableSet()
            val grey = (prefs[KEY_GREYLIST] ?: emptySet()).toMutableSet()

            black.remove(packageName)
            white.remove(packageName)
            grey.remove(packageName)

            when (category) {
                AppCategory.BLACKLIST -> black.add(packageName)
                AppCategory.WHITELIST -> white.add(packageName)
                AppCategory.GREYLIST -> grey.add(packageName)
                AppCategory.UNMANAGED -> Unit
            }

            prefs[KEY_BLACKLIST] = black
            prefs[KEY_WHITELIST] = white
            prefs[KEY_GREYLIST] = grey
        }
    }

    companion object {
        private val KEY_BLACKLIST = stringSetPreferencesKey("user_blacklist")
        private val KEY_WHITELIST = stringSetPreferencesKey("user_whitelist")
        private val KEY_GREYLIST = stringSetPreferencesKey("user_greylist")
    }
}
