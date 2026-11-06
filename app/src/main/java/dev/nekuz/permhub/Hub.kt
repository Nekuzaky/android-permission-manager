package dev.nekuz.permhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

object Hub {
    private const val APPWALL_PACKAGE = "dev.nekuz.appwall"
    private const val APPWALL_CERT_SHA256 = "5b337ce0f98772c46a9b7ee869312fc6c42596c2036d7910643991b549acd031"
    private const val MANAGE_ALL_FILES = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"
    private const val ICON_SIZE_PX = 144
    private const val ICON_CACHE_SIZE = 256
    private const val MAX_ICON_PIXELS = 16_000_000L

    private val main = Handler(Looper.getMainLooper())
    private val iconExecutor = Executors.newSingleThreadExecutor()
    private val icons = LruCache<String, Bitmap>(ICON_CACHE_SIZE)

    fun openAppSettings(activity: Activity, packageName: String) {
        open(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName)))
    }

    fun openSpecialSettings(activity: Activity, access: SpecialAccess, packageName: String?) {
        val uri = packageName?.let(::packageUri)
        val intent = when (access) {
            SpecialAccess.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SpecialAccess.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SpecialAccess.DEVICE_ADMIN -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            SpecialAccess.VPN -> Intent(Settings.ACTION_VPN_SETTINGS)
            SpecialAccess.OVERLAY -> withData(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
            SpecialAccess.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SpecialAccess.ALL_FILES ->
                if (uri != null) Intent(MANAGE_ALL_FILES, uri)
                else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            SpecialAccess.INSTALL -> withData(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
            SpecialAccess.WRITE_SETTINGS -> withData(Settings.ACTION_MANAGE_WRITE_SETTINGS, uri)
        }
        open(activity, intent, packageName)
    }

    fun appWallIntent(context: Context): Intent? = runCatching {
        val packageManager = context.packageManager
        val verified = packageManager.hasSigningCertificate(
            APPWALL_PACKAGE,
            Text.hexToBytes(APPWALL_CERT_SHA256),
            PackageManager.CERT_INPUT_SHA256,
        )
        if (verified) packageManager.getLaunchIntentForPackage(APPWALL_PACKAGE) else null
    }.getOrNull()

    fun bindIcon(context: Context, view: ImageView, packageName: String) {
        view.tag = packageName
        val cached = icons.get(packageName)
        view.setImageBitmap(cached)
        if (cached != null) return
        val appContext = context.applicationContext
        iconExecutor.execute {
            val icon = loadIcon(appContext, packageName) ?: return@execute
            icons.put(packageName, icon)
            main.post { if (view.tag == packageName) view.setImageBitmap(icon) }
        }
    }

    fun bindNotice(view: TextView, outdated: Boolean, unknown: Set<SpecialAccess>) {
        val context = view.context
        val lines = buildList {
            if (outdated) add(context.getString(R.string.notice_outdated))
            if (unknown.isNotEmpty()) {
                val titles = unknown.joinToString { context.getString(it.title) }
                add(context.getString(R.string.notice_unknown, titles))
            }
        }
        view.text = lines.joinToString("\n")
        view.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
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

    private fun loadIcon(context: Context, packageName: String): Bitmap? =
        runCatching {
            check(!isRasterIconTooLarge(context.packageManager, packageName))
            render(context.packageManager.getApplicationIcon(packageName))
        }.getOrNull() ?: runCatching {
            render(requireNotNull(context.getDrawable(android.R.drawable.sym_def_app_icon)))
        }.getOrNull()

    private fun isRasterIconTooLarge(packageManager: PackageManager, packageName: String): Boolean = runCatching {
        val application = packageManager.getApplicationInfo(packageName, 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        packageManager.getResourcesForApplication(application).openRawResource(application.icon).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_ICON_PIXELS
    }.getOrDefault(false)

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun packageUri(packageName: String) = Uri.parse("package:$packageName")

    private fun withData(action: String, uri: Uri?) = if (uri != null) Intent(action, uri) else Intent(action)

    private fun pinnedToSystem(activity: Activity, intent: Intent): Intent {
        val target = runCatching {
            activity.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull()
                ?.activityInfo
                ?.packageName
        }.getOrNull() ?: return intent
        return Intent(intent).setPackage(target)
    }

    private fun open(activity: Activity, intent: Intent, fallbackPackage: String? = null) {
        try {
            activity.startActivity(pinnedToSystem(activity, intent))
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
