package dev.nekuz.permhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

object Hub {
    private const val APPWALL_PACKAGE = "dev.nekuz.appwall"
    private const val MANAGE_ALL_FILES = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"

    private val main = Handler(Looper.getMainLooper())
    private val iconExecutor = Executors.newSingleThreadExecutor()
    private val icons = HashMap<String, Drawable>()

    fun openAppSettings(activity: Activity, packageName: String) {
        open(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName)))
    }

    fun openSpecialSettings(activity: Activity, access: SpecialAccess, packageName: String?) {
        val uri = packageName?.let(::packageUri)
        val intent = when (access) {
            SpecialAccess.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SpecialAccess.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SpecialAccess.DEVICE_ADMIN -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            SpecialAccess.OVERLAY -> withData(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            SpecialAccess.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SpecialAccess.ALL_FILES ->
                if (uri != null) Intent(MANAGE_ALL_FILES, uri)
                else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            SpecialAccess.INSTALL -> withData(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
        }
        open(activity, intent, packageName)
    }

    fun appWallIntent(context: Context): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(APPWALL_PACKAGE) }.getOrNull()

    fun bindIcon(context: Context, view: ImageView, packageName: String) {
        view.tag = packageName
        val cached = synchronized(icons) { icons[packageName] }
        view.setImageDrawable(cached)
        if (cached != null) return
        val appContext = context.applicationContext
        iconExecutor.execute {
            val icon = runCatching { appContext.packageManager.getApplicationIcon(packageName) }.getOrNull()
                ?: return@execute
            synchronized(icons) { icons[packageName] = icon }
            main.post { if (view.tag == packageName) view.setImageDrawable(icon) }
        }
    }

    fun bindSeverity(view: TextView, severity: Severity?) {
        if (severity == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.setText(severity.label)
        val color = when (severity) {
            Severity.HIGH -> R.color.danger
            Severity.MEDIUM -> R.color.amber
            Severity.LOW -> R.color.text_secondary
        }
        view.setTextColor(view.context.getColor(color))
    }

    private fun packageUri(packageName: String) = Uri.parse("package:$packageName")

    private fun withData(action: String, uri: Uri?) = if (uri != null) Intent(action, uri) else Intent(action)

    private fun open(activity: Activity, intent: Intent, fallbackPackage: String? = null) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallbackPackage != null) {
                openAppSettings(activity, fallbackPackage)
            } else {
                Toast.makeText(activity, R.string.open_failed, Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(activity, R.string.open_failed, Toast.LENGTH_LONG).show()
        }
    }
}
