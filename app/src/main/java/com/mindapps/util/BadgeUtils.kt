package com.mindapps.util

import android.content.Context
import me.leolin.shortcutbadger.ShortcutBadger

object BadgeUtils {

    fun setLauncherBadge(context: Context, count: Int) {
        try {
            if (count > 0) {
                ShortcutBadger.applyCount(context, count)
            } else {
                ShortcutBadger.removeCount(context)
            }
        } catch (e: Exception) {
            // Badge not supported on this launcher
        }
    }

    fun clearLauncherBadge(context: Context) {
        try {
            ShortcutBadger.removeCount(context)
        } catch (e: Exception) {
            // Badge not supported on this launcher
        }
    }
}
