package dev.nekuz.permhub

import android.content.Context

object Inventory {
    @Volatile
    var result: ScanResult = ScanResult(emptyList(), emptySet())

    fun refresh(context: Context): ScanResult {
        val scanned = Scanner.scan(context.applicationContext)
        result = scanned
        return scanned
    }
}
