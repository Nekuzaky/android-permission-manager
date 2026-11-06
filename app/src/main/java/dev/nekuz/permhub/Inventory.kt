package dev.nekuz.permhub

import android.content.Context

object Inventory {
    @Volatile
    var apps: List<AppFacts> = emptyList()

    fun refresh(context: Context): List<AppFacts> {
        val scanned = Scanner.scan(context.applicationContext)
        apps = scanned
        return scanned
    }
}
