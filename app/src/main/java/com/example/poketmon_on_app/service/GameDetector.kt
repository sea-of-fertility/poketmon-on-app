package com.example.poketmon_on_app.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import kotlin.concurrent.thread

class GameDetector(
    private val context: Context,
    private val mainHandler: Handler,
    private val onHide: () -> Unit,
    private val onRestore: () -> Unit
) {

    private var enabled = false
    private var isHidden = false
    private val launcherPackage: String? = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .let { context.packageManager.resolveActivity(it, 0)?.activityInfo?.packageName }

    private val checkRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!enabled) return
            val self = this
            thread {
                val isGame = isForegroundAppGame()
                mainHandler.post {
                    if (!enabled) return@post
                    if (isGame && !isHidden) {
                        isHidden = true
                        onHide()
                    } else if (!isGame && isHidden) {
                        isHidden = false
                        onRestore()
                    }
                    mainHandler.postDelayed(self, 3000L)
                }
            }
        }
    }

    fun startIfEnabled(hideInGame: Boolean) {
        mainHandler.removeCallbacks(checkRunnable)
        enabled = hideInGame && hasUsageStatsPermission()
        if (enabled) {
            mainHandler.post(checkRunnable)
        } else if (isHidden) {
            isHidden = false
            onRestore()
        }
    }

    fun stop() {
        mainHandler.removeCallbacks(checkRunnable)
        enabled = false
    }

    fun destroy() {
        stop()
        if (isHidden) {
            isHidden = false
            onRestore()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return stats != null && stats.isNotEmpty()
    }

    private fun isForegroundAppGame(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        if (stats.isNullOrEmpty()) return false

        val foreground = stats.maxByOrNull { it.lastTimeUsed } ?: return false
        val pkg = foreground.packageName

        if (pkg == context.packageName || pkg == "com.android.systemui" || pkg == launcherPackage) {
            return false
        }

        return try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            appInfo.category == ApplicationInfo.CATEGORY_GAME
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
