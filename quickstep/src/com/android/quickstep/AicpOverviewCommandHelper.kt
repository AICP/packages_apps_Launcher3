package com.android.quickstep

import android.content.ContentResolver;
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import com.android.systemui.slimrecent.RecentController

class AicpOverviewCommandHelper(
    val context: Context
) {
    private var useSlimRecents = false

    private val slimRecents = RecentController().also {
        it.onStart(context)
    }

    private val settingsObserver = object : ContentObserver(Handler()) {
        fun observe() {
            val resolver: ContentResolver = context.getContentResolver()
            resolver.registerContentObserver(
                Settings.System.getUriFor(
                    Settings.System.USE_SLIM_RECENTS
                ), false, this
            )
            update()
        }

        fun unobserve() { // TODO call onDestroy of launcher (could use lifecycleTracker?)
            context.getContentResolver().unregisterContentObserver(this)
        }

        public override fun onChange(selfChange: Boolean) {
            update()
        }

        fun update() {
            val resolver = context.getContentResolver()

            useSlimRecents = Settings.System.getInt(resolver, Settings.System.USE_SLIM_RECENTS, 0) == 1
        }
    }.also {
        it.observe()
    }


    fun overrideExecuteCommand(type: OverviewCommandHelper.CommandType): Boolean {
        if (!useSlimRecents) {
            return false
        }
        return when (type) {
            OverviewCommandHelper.CommandType.TOGGLE -> {
                slimRecents.toggleRecentApps()
                true
            }
            OverviewCommandHelper.CommandType.SHOW_ALT_TAB -> {
                slimRecents.showRecentApps()
                true
            }
            OverviewCommandHelper.CommandType.HIDE_ALT_TAB -> {
                slimRecents.hideRecents(false)
            }
            else -> false
        }
    }
}
