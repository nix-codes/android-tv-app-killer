package com.appkiller.tv

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log

object RunningAppsHelper {

    val killedAt = mutableMapOf<String, Long>()

    private const val TAG = "AppKiller"

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        Log.d(TAG, "hasUsageStatsPermission: mode=$mode allowed=${mode == AppOpsManager.MODE_ALLOWED}")
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getRunningUserApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000L * 60 * 60 * 24 // last 24 hours

        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        Log.d(TAG, "queryAndAggregateUsageStats returned ${statsMap.size} entries")

        // Base: all installed packages that are not force-stopped
        val installedPackages = pm.getInstalledPackages(0)
        Log.d(TAG, "Installed packages: ${installedPackages.size}")

        val filtered = installedPackages
            .filter { pkg ->
                val info = pkg.applicationInfo ?: return@filter false
                // exclude system apps
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .filter { pkg ->
                val info = pkg.applicationInfo ?: return@filter false
                // exclude explicitly force-stopped apps
                (info.flags and ApplicationInfo.FLAG_STOPPED) == 0
            }
            .filter { pkg -> pkg.packageName != context.packageName }
            .mapNotNull { pkg ->
                // must have been used in the last 24 hours
                val stat = statsMap[pkg.packageName] ?: return@mapNotNull null
                if (stat.lastTimeUsed <= 0) return@mapNotNull null
                Pair(pkg, stat.lastTimeUsed)
            }
            .filter { (pkg, lastUsed) ->
                // exclude apps we killed that haven't been relaunched since
                val kt = killedAt[pkg.packageName]
                kt == null || lastUsed > kt
            }

        Log.d(TAG, "After filtering: ${filtered.size} entries")

        return filtered
            .sortedByDescending { (_, lastUsed) -> lastUsed }
            .mapNotNull { (pkg, _) ->
                val info = pkg.applicationInfo ?: return@mapNotNull null
                try {
                    AppItem(
                        packageName = pkg.packageName,
                        appName = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info)
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
    }
}
