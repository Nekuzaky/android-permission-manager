package dev.nekuz.permhub

enum class Severity(val label: Int) {
    LOW(R.string.severity_low),
    MEDIUM(R.string.severity_medium),
    HIGH(R.string.severity_high),
}

data class Finding(val severity: Severity, val reason: Int)

data class AppFacts(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isGame: Boolean,
    val allowed: Set<PermissionCategory>,
    val requested: Set<PermissionCategory>,
    val special: Set<SpecialAccess>,
    val requestedSpecial: Set<SpecialAccess>,
)

object Risk {
    private val UTILITY_KEYWORDS = listOf(
        "flashlight",
        "torch",
        "lampe",
        "calculator",
        "calculatrice",
        "wallpaper",
        "fond d'écran",
        "compass",
        "boussole",
        "qr scanner",
        "qr code",
        "unit converter",
        "battery",
        "cleaner",
    )

    private val UTILITY_SENSITIVE = setOf(
        PermissionCategory.MICROPHONE,
        PermissionCategory.CONTACTS,
        PermissionCategory.SMS,
        PermissionCategory.CALL_LOG,
        PermissionCategory.BACKGROUND_LOCATION,
    )

    private val GAME_SENSITIVE = setOf(
        PermissionCategory.SMS,
        PermissionCategory.CONTACTS,
        PermissionCategory.CALL_LOG,
    )

    private val PERMISSION_FINDINGS = mapOf(
        PermissionCategory.SMS to Finding(Severity.HIGH, R.string.reason_sms),
        PermissionCategory.CALL_LOG to Finding(Severity.HIGH, R.string.reason_call_log),
        PermissionCategory.BACKGROUND_LOCATION to Finding(Severity.HIGH, R.string.reason_background_location),
        PermissionCategory.MICROPHONE to Finding(Severity.MEDIUM, R.string.reason_microphone),
        PermissionCategory.CAMERA to Finding(Severity.MEDIUM, R.string.reason_camera),
        PermissionCategory.CONTACTS to Finding(Severity.MEDIUM, R.string.reason_contacts),
        PermissionCategory.LOCATION to Finding(Severity.MEDIUM, R.string.reason_location),
        PermissionCategory.PHONE to Finding(Severity.MEDIUM, R.string.reason_phone),
        PermissionCategory.CALENDAR to Finding(Severity.MEDIUM, R.string.reason_calendar),
        PermissionCategory.MEDIA to Finding(Severity.LOW, R.string.reason_media),
        PermissionCategory.SENSORS to Finding(Severity.LOW, R.string.reason_sensors),
        PermissionCategory.NEARBY to Finding(Severity.LOW, R.string.reason_nearby),
        PermissionCategory.NOTIFICATIONS to Finding(Severity.LOW, R.string.reason_notifications),
    )

    private val SPECIAL_FINDINGS = mapOf(
        SpecialAccess.ACCESSIBILITY to Finding(Severity.HIGH, R.string.reason_accessibility),
        SpecialAccess.NOTIFICATION_LISTENER to Finding(Severity.HIGH, R.string.reason_notification_listener),
        SpecialAccess.DEVICE_ADMIN to Finding(Severity.HIGH, R.string.reason_device_admin),
        SpecialAccess.OVERLAY to Finding(Severity.MEDIUM, R.string.reason_overlay),
        SpecialAccess.USAGE to Finding(Severity.MEDIUM, R.string.reason_usage),
        SpecialAccess.ALL_FILES to Finding(Severity.MEDIUM, R.string.reason_all_files),
        SpecialAccess.INSTALL to Finding(Severity.MEDIUM, R.string.reason_install),
    )

    fun findings(app: AppFacts): List<Finding> {
        val found = mutableListOf<Finding>()
        if (isUtility(app.label) && app.allowed.any { it in UTILITY_SENSITIVE }) {
            found += Finding(Severity.HIGH, R.string.reason_unusual_utility)
        }
        if (app.isGame && app.allowed.any { it in GAME_SENSITIVE }) {
            found += Finding(Severity.HIGH, R.string.reason_unusual_game)
        }
        app.special.mapNotNullTo(found) { SPECIAL_FINDINGS[it] }
        app.allowed.mapNotNullTo(found) { PERMISSION_FINDINGS[it] }
        val adjusted = if (app.isSystem) found.map { it.copy(severity = lowerForSystem(it.severity)) } else found
        return adjusted.sortedByDescending { it.severity }
    }

    fun level(app: AppFacts): Severity? = findings(app).firstOrNull()?.severity

    fun isUtility(label: String): Boolean {
        val lower = label.lowercase()
        return UTILITY_KEYWORDS.any { it in lower }
    }

    private fun lowerForSystem(severity: Severity) =
        if (severity == Severity.HIGH) Severity.MEDIUM else severity
}
