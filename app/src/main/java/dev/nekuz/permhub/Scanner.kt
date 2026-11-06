package dev.nekuz.permhub

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object Scanner {
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
    private const val OP_PLATFORM_VPN = "android:activate_platform_vpn"

    private enum class AccessState { ALLOWED, DENIED, UNKNOWN }

    private class Signals(
        val accessibility: Set<String>,
        val listeners: Set<String>,
        val admins: Set<String>,
        val vpnServices: Set<String>,
        val unknown: MutableSet<SpecialAccess>,
    )

    fun scan(context: Context): ScanResult {
        val packageManager = context.packageManager
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val accessibility = accessibilityPackages(context)
        val listeners = listenerPackages(context)
        val admins = adminPackages(context)
        val unknown = mutableSetOf<SpecialAccess>()
        if (accessibility == null) unknown += SpecialAccess.ACCESSIBILITY
        if (listeners == null) unknown += SpecialAccess.NOTIFICATION_LISTENER
        if (admins == null) unknown += SpecialAccess.DEVICE_ADMIN
        val signals = Signals(
            accessibility.orEmpty(),
            listeners.orEmpty(),
            admins.orEmpty(),
            vpnServicePackages(packageManager),
            unknown,
        )

        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.packageName != context.packageName && it.applicationInfo != null }
        val facts = packages.map { info ->
            runCatching { describe(packageManager, appOps, info, signals) }.getOrElse { unreadable(info) }
        }
        val apps = mergeShared(packageManager, packages, facts).sortedBy { it.label.lowercase() }
        return ScanResult(apps, unknown)
    }

    private fun describe(
        packageManager: PackageManager,
        appOps: AppOpsManager,
        info: PackageInfo,
        signals: Signals,
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
        val unknownSpecial = mutableSetOf<SpecialAccess>()
        if (info.packageName in signals.accessibility) special += SpecialAccess.ACCESSIBILITY
        if (info.packageName in signals.listeners) special += SpecialAccess.NOTIFICATION_LISTENER
        if (info.packageName in signals.admins) special += SpecialAccess.DEVICE_ADMIN
        for (access in SpecialAccess.entries) {
            val permission = access.permission ?: continue
            if (permission !in names) continue
            requestedSpecial += access
            when (specialState(packageManager, appOps, access, application)) {
                AccessState.ALLOWED -> special += access
                AccessState.UNKNOWN -> unknownSpecial += access
                AccessState.DENIED -> Unit
            }
        }
        if (info.packageName in signals.vpnServices) requestedSpecial += SpecialAccess.VPN
        when (vpnState(appOps, application)) {
            AccessState.ALLOWED -> {
                special += SpecialAccess.VPN
                requestedSpecial += SpecialAccess.VPN
            }
            AccessState.UNKNOWN -> signals.unknown += SpecialAccess.VPN
            AccessState.DENIED -> Unit
        }

        val label = runCatching { application.loadLabel(packageManager).toString() }

        return AppFacts(
            packageName = info.packageName,
            label = Text.cleanLabel(label.getOrNull(), info.packageName),
            isSystem = application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isGame = application.category == ApplicationInfo.CATEGORY_GAME,
            allowed = allowed,
            requested = requested,
            special = special,
            requestedSpecial = requestedSpecial,
            unknownSpecial = unknownSpecial,
            unreadable = label.isFailure,
        )
    }

    private fun unreadable(info: PackageInfo): AppFacts {
        val flags = info.applicationInfo?.flags ?: 0
        return AppFacts(
            packageName = info.packageName,
            label = info.packageName,
            isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isGame = false,
            allowed = emptySet(),
            requested = emptySet(),
            special = emptySet(),
            requestedSpecial = emptySet(),
            unreadable = true,
        )
    }

    @Suppress("DEPRECATION")
    private fun mergeShared(
        packageManager: PackageManager,
        packages: List<PackageInfo>,
        facts: List<AppFacts>,
    ): List<AppFacts> {
        val byPackage = facts.associateBy { it.packageName }
        return packages.mapIndexed { index, info ->
            val own = facts[index]
            val uid = info.applicationInfo?.uid
            if (info.sharedUserId == null || uid == null) return@mapIndexed own
            val siblings = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull().orEmpty()
                .filter { it != own.packageName }
                .mapNotNull { byPackage[it] }
            if (siblings.isEmpty()) own else own.mergedWith(siblings)
        }
    }

    private fun isLegacy(application: ApplicationInfo) = application.targetSdkVersion < Build.VERSION_CODES.M

    private fun isOpAllowed(appOps: AppOpsManager, permission: String, application: ApplicationInfo): Boolean {
        val op = AppOpsManager.permissionToOp(permission) ?: return true
        val mode = runCatching {
            appOps.unsafeCheckOpRawNoThrow(op, application.uid, application.packageName)
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        return mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
    }

    private fun specialState(
        packageManager: PackageManager,
        appOps: AppOpsManager,
        access: SpecialAccess,
        application: ApplicationInfo,
    ): AccessState {
        val op = access.appOp ?: return AccessState.UNKNOWN
        val permission = access.permission ?: return AccessState.UNKNOWN
        return runCatching {
            when (appOps.unsafeCheckOpRawNoThrow(op, application.uid, application.packageName)) {
                AppOpsManager.MODE_ALLOWED -> AccessState.ALLOWED
                AppOpsManager.MODE_DEFAULT ->
                    if (packageManager.checkPermission(permission, application.packageName) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        AccessState.ALLOWED
                    } else {
                        AccessState.DENIED
                    }
                else -> AccessState.DENIED
            }
        }.getOrDefault(AccessState.UNKNOWN)
    }

    private fun vpnState(appOps: AppOpsManager, application: ApplicationInfo): AccessState {
        val op = SpecialAccess.VPN.appOp ?: return AccessState.UNKNOWN
        val mode = runCatching {
            appOps.unsafeCheckOpRawNoThrow(op, application.uid, application.packageName)
        }.getOrNull() ?: return AccessState.UNKNOWN
        val platformMode = runCatching {
            appOps.unsafeCheckOpRawNoThrow(OP_PLATFORM_VPN, application.uid, application.packageName)
        }.getOrNull()
        val approved = mode == AppOpsManager.MODE_ALLOWED || platformMode == AppOpsManager.MODE_ALLOWED
        return if (approved) AccessState.ALLOWED else AccessState.DENIED
    }

    private fun vpnServicePackages(packageManager: PackageManager): Set<String> = runCatching {
        packageManager.queryIntentServices(
            Intent(VpnService.SERVICE_INTERFACE),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
            .mapNotNull { it.serviceInfo }
            .filter { it.permission == Manifest.permission.BIND_VPN_SERVICE }
            .map { it.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun accessibilityPackages(context: Context): Set<String>? {
        val bound = runCatching {
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
                .toSet()
        }.getOrNull()
        val enabled = runCatching {
            Text.packagesOf(
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            )
        }.getOrNull()
        if (bound == null && enabled == null) return null
        return bound.orEmpty() + enabled.orEmpty()
    }

    private fun listenerPackages(context: Context): Set<String>? = runCatching {
        Text.packagesOf(Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS))
    }.getOrNull()

    private fun adminPackages(context: Context): Set<String>? = runCatching {
        context.getSystemService(DevicePolicyManager::class.java)
            .activeAdmins
            .orEmpty()
            .map { it.packageName }
            .toSet()
    }.getOrNull()
}
