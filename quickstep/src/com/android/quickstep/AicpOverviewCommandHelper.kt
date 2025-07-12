package com.android.quickstep

import android.content.Context
import android.provider.Settings
import com.android.systemui.slimrecent.RecentController

class AicpOverviewCommandHelper(
    val context: Context
) {
    // TODO UPDATE on settings change
    private var useSlimRecents =
        Settings.System.getInt(context.getContentResolver(), Settings.System.USE_SLIM_RECENTS, 0) == 1

    private val slimRecents = RecentController().also {
        it.onStart(context)
    }

    fun overrideExecuteCommand(type: OverviewCommandHelper.CommandType): Boolean {
        if (!useSlimRecents && false) { // TODO not hardcode on
            return false
        }
        return when (type) {
            OverviewCommandHelper.CommandType.TOGGLE -> {
                slimRecents.toggleRecentApps()
                true
            }
            OverviewCommandHelper.CommandType.SHOW -> {
                slimRecents.showRecentApps()
                true
            }
            OverviewCommandHelper.CommandType.HIDE -> {
                slimRecents.hideRecents(false)
            }
            else -> false
        }
    }
}
