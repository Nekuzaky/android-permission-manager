package dev.nekuz.permhub

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object Scanner {
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    fun scan(context: Context): List<AppFacts> {
        val packageManager = context.packageManager
        val accessibility = accessibilityPackages(context)
        val listeners = listenerPackages(context)
        val admins = adminPackages(context)
        val appOps = context.getSystemService(AppOpsManager::class.java)

        return packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.packageName != context.packageName && it.applicationInfo != null }
            .map { info -> describe(packageManager, appOps, info, accessibility, listeners, admins) }
            .sortedBy { it.label.lowercase() }
    }

    private fun describe(
        packageManager: PackageManager,
        appOps: AppOpsManager,
        info: PackageInfo,
        accessibility: Set<String>,
        listeners: Set<String>,
        admins: Set<String>,
    ): AppFacts {
        val application = requireNotNull(info.applicationInfo)
        val names = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: IntArray(names.size)
        val requested = mutableSetOf<PermissionCategory>()
        val allowed = mutableSetOf<PermissionCategory>()

        names.forEachIndexed { index, name ->
            val category = PermissionCategory.of(name) ?: return@forEachIndexed
            if (Build.VERSION.SDK_INT < category.minSdk) return@forEachIndexed
            requested += category
            val granted = flags.getOrElse(index) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            if (granted && (!isLegacy(application) || isOpAllowed(appOps, name, application))) allowed += category
        }

        val special = mutableSetOf<SpecialAccess>()
        val requestedSpecial = mutableSetOf<SpecialAccess>()
        if (info.packageName in accessibility) special += SpecialAccess.ACCESSIBILITY
        if (info.packageName in listeners) special += SpecialAccess.NOTIFICATION_LISTENER
        if (info.packageName in admins) special += SpecialAccess.DEVICE_ADMIN
        for (access in SpecialAccess.entries) {
            val permission = access.permission ?: continue
            if (permission !in names) continue
            requestedSpecial += access
            if (isSpecialAllowed(packageManager, appOps, access, application)) special += access
        }

        return AppFacts(
            packageName = info.packageName,
            label = application.loadLabel(packageManager).toString(),
            isSystem = application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isGame = application.category == ApplicationInfo.CATEGORY_GAME,
            allowed = allowed,
            requested = requested,
            special = special,
            requestedSpecial = requestedSpecial,
        )
    }

    private fun isLegacy(application: ApplicationInfo) = application.targetSdkVersion < Build.VERSION_CODES.M

    private fun isOpAllowed(appOps: AppOpsManager, permission: String, application: ApplicationInfo): Boolean {
        val op = AppOpsManager.permissionToOp(permission) ?: return true
        val mode = runCatching {
            appOps.unsafeCheckOpRawNoThrow(op, application.uid, application.packageName)
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        return mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
    }

    private fun isSpecialAllowed(
        packageManager: PackageManager,
        appOps: AppOpsManager,
        access: SpecialAccess,
        application: ApplicationInfo,
    ): Boolean {
        val op = access.appOp ?: return false
        val permission = access.permission ?: return false
        val mode = runCatching {
            appOps.unsafeCheckOpRawNoThrow(op, application.uid, application.packageName)
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                packageManager.checkPermission(permission, application.packageName) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    private fun accessibilityPackages(context: Context): Set<String> = runCatching {
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun listenerPackages(context: Context): Set<String> = runCatching {
        Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun adminPackages(context: Context): Set<String> = runCatching {
        context.getSystemService(DevicePolicyManager::class.java)
            .activeAdmins
            .orEmpty()
            .map { it.packageName }
            .toSet()
    }.getOrDefault(emptySet())
}
