package com.appkiller.tv

import android.content.Context

object WhitelistRepository {
    private const val PREFS_NAME = "appkiller_prefs"
    private const val KEY_WHITELIST = "whitelist"

    fun load(context: Context): Set<String> =
        HashSet(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        )

    fun save(context: Context, whitelist: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_WHITELIST, HashSet(whitelist)).apply()
    }
}
