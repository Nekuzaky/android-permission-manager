package dev.nekuz.permhub

enum class PermissionCategory(val title: Int, val permissions: Set<String>, val minSdk: Int = 0) {
    MICROPHONE(R.string.permission_microphone, setOf("android.permission.RECORD_AUDIO")),
    CAMERA(R.string.permission_camera, setOf("android.permission.CAMERA")),
    LOCATION(
        R.string.permission_location,
        setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
    ),
    BACKGROUND_LOCATION(
        R.string.permission_background_location,
        setOf("android.permission.ACCESS_BACKGROUND_LOCATION"),
    ),
    CONTACTS(
        R.string.permission_contacts,
        setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
        ),
    ),
    PHONE(
        R.string.permission_phone,
        setOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
        ),
    ),
    CALL_LOG(
        R.string.permission_call_log,
        setOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS",
        ),
    ),
    SMS(
        R.string.permission_sms,
        setOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH",
        ),
    ),
    CALENDAR(
        R.string.permission_calendar,
        setOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"),
    ),
    MEDIA(
        R.string.permission_media,
        setOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ),
    ),
    SENSORS(
        R.string.permission_sensors,
        setOf(
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.ACTIVITY_RECOGNITION",
        ),
    ),
    NEARBY(
        R.string.permission_nearby,
        setOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.UWB_RANGING",
        ),
        31,
    ),
    NOTIFICATIONS(R.string.permission_notifications, setOf("android.permission.POST_NOTIFICATIONS"), 33);

    companion object {
        private val byPermission = entries.flatMap { category ->
            category.permissions.map { it to category }
        }.toMap()

        fun of(permission: String): PermissionCategory? = byPermission[permission]

        fun availableOn(sdk: Int): List<PermissionCategory> = entries.filter { sdk >= it.minSdk }
    }
}

enum class SpecialAccess(val title: Int, val permission: String?, val appOp: String?) {
    ACCESSIBILITY(R.string.special_accessibility, null, null),
    NOTIFICATION_LISTENER(R.string.special_notification_listener, null, null),
    DEVICE_ADMIN(R.string.special_device_admin, null, null),
    OVERLAY(
        R.string.special_overlay,
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android:system_alert_window",
    ),
    USAGE(R.string.special_usage, "android.permission.PACKAGE_USAGE_STATS", "android:get_usage_stats"),
    ALL_FILES(
        R.string.special_all_files,
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android:manage_external_storage",
    ),
    INSTALL(
        R.string.special_install,
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android:request_install_packages",
    ),
}
