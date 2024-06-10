package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalPullToSwitchArticle =
    compositionLocalOf<PullToSwitchArticlePreference> { PullToSwitchArticlePreference.default }


sealed class PullToSwitchArticlePreference(val value: Int) : Preference() {
    object NoneSwitch : PullToSwitchArticlePreference(0)
    object VerticalSwitch : PullToSwitchArticlePreference(1)
    object HorizontalSwitch : PullToSwitchArticlePreference(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKey.pullToSwitchArticle, value)
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            HorizontalSwitch -> context.getString(R.string.horizontal_switch)
            NoneSwitch -> context.getString(R.string.none_switch)
            VerticalSwitch -> context.getString(R.string.vertical_switch)
        }

    fun equals(other: PullToSwitchArticlePreference): Boolean {
        return this.value.compareTo(other.value) == 0
    }

    companion object {
        val default = PullToSwitchArticlePreference.NoneSwitch
        val values = listOf(
            NoneSwitch,
            VerticalSwitch,
            HorizontalSwitch
        )

        fun fromPreferences(preferences: Preferences): PullToSwitchArticlePreference {
            return when (preferences[DataStoreKey.keys[DataStoreKey.pullToSwitchArticle]?.key as Preferences.Key<Int>]) {
                0 -> NoneSwitch
                1 -> VerticalSwitch
                2 -> HorizontalSwitch
                else -> PullToSwitchArticlePreference.default
            }
        }
    }
}
