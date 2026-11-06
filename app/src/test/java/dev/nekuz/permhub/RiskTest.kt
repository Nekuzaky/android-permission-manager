package dev.nekuz.permhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskTest {

    private fun app(
        label: String = "Sample",
        isSystem: Boolean = false,
        isGame: Boolean = false,
        allowed: Set<PermissionCategory> = emptySet(),
        special: Set<SpecialAccess> = emptySet(),
    ) = AppFacts("com.example", label, isSystem, isGame, allowed, allowed, special, special)

    @Test
    fun appWithNothingHasNoLevel() {
        assertNull(Risk.level(app()))
    }

    @Test
    fun requestedButNotAllowedDoesNotCount() {
        val facts = app().copy(requested = setOf(PermissionCategory.SMS))
        assertNull(Risk.level(facts))
    }

    @Test
    fun smsIsHigh() {
        assertEquals(Severity.HIGH, Risk.level(app(allowed = setOf(PermissionCategory.SMS))))
    }

    @Test
    fun cameraIsMedium() {
        assertEquals(Severity.MEDIUM, Risk.level(app(allowed = setOf(PermissionCategory.CAMERA))))
    }

    @Test
    fun notificationsAreLow() {
        assertEquals(Severity.LOW, Risk.level(app(allowed = setOf(PermissionCategory.NOTIFICATIONS))))
    }

    @Test
    fun accessibilityIsHigh() {
        assertEquals(Severity.HIGH, Risk.level(app(special = setOf(SpecialAccess.ACCESSIBILITY))))
    }

    @Test
    fun systemAppsAreNeverHigh() {
        val facts = app(
            isSystem = true,
            allowed = setOf(PermissionCategory.SMS),
            special = setOf(SpecialAccess.ACCESSIBILITY),
        )
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun flashlightWithMicrophoneIsFlaggedAsUnusual() {
        val findings = Risk.findings(app(label = "Super Flashlight", allowed = setOf(PermissionCategory.MICROPHONE)))
        assertEquals(R.string.reason_unusual_utility, findings.first().reason)
        assertEquals(Severity.HIGH, findings.first().severity)
    }

    @Test
    fun flashlightWithCameraIsNotUnusual() {
        val findings = Risk.findings(app(label = "Super Flashlight", allowed = setOf(PermissionCategory.CAMERA)))
        assertFalse(findings.any { it.reason == R.string.reason_unusual_utility })
    }

    @Test
    fun gameWithContactsIsFlaggedAsUnusual() {
        val findings = Risk.findings(app(isGame = true, allowed = setOf(PermissionCategory.CONTACTS)))
        assertEquals(R.string.reason_unusual_game, findings.first().reason)
    }

    @Test
    fun findingsAreSortedBySeverity() {
        val findings = Risk.findings(
            app(allowed = setOf(PermissionCategory.NOTIFICATIONS, PermissionCategory.CAMERA, PermissionCategory.SMS))
        )
        assertEquals(listOf(Severity.HIGH, Severity.MEDIUM, Severity.LOW), findings.map { it.severity })
    }

    @Test
    fun utilityMatchIsCaseInsensitive() {
        assertTrue(Risk.isUtility("QR CODE reader"))
        assertFalse(Risk.isUtility("Instagram"))
    }

    @Test
    fun everyPermissionCategoryProducesAFinding() {
        for (category in PermissionCategory.entries) {
            assertEquals(category.name, 1, Risk.findings(app(allowed = setOf(category))).size)
        }
    }

    @Test
    fun threeSensitivePermissionsEscalate() {
        val facts = app(
            allowed = setOf(PermissionCategory.MICROPHONE, PermissionCategory.CAMERA, PermissionCategory.LOCATION),
        )
        assertEquals(Severity.HIGH, Risk.level(facts))
        assertEquals(R.string.reason_combination, Risk.findings(facts).first().reason)
    }

    @Test
    fun specialAccessCountsTowardCombination() {
        val facts = app(
            allowed = setOf(PermissionCategory.CALENDAR),
            special = setOf(SpecialAccess.USAGE, SpecialAccess.INSTALL),
        )
        assertEquals(Severity.HIGH, Risk.level(facts))
    }

    @Test
    fun twoSensitivePermissionsDoNotEscalate() {
        val facts = app(allowed = setOf(PermissionCategory.CAMERA, PermissionCategory.LOCATION))
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun lowFindingsDoNotCountTowardCombination() {
        val facts = app(
            allowed = setOf(
                PermissionCategory.CAMERA,
                PermissionCategory.LOCATION,
                PermissionCategory.MEDIA,
                PermissionCategory.NOTIFICATIONS,
            ),
        )
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun overlayWithMicrophoneEscalates() {
        val facts = app(allowed = setOf(PermissionCategory.MICROPHONE), special = setOf(SpecialAccess.OVERLAY))
        assertEquals(Severity.HIGH, Risk.level(facts))
    }

    @Test
    fun allFilesWithContactsEscalates() {
        val facts = app(allowed = setOf(PermissionCategory.CONTACTS), special = setOf(SpecialAccess.ALL_FILES))
        assertEquals(Severity.HIGH, Risk.level(facts))
    }

    @Test
    fun overlayAloneStaysSensitive() {
        assertEquals(Severity.MEDIUM, Risk.level(app(special = setOf(SpecialAccess.OVERLAY))))
    }

    @Test
    fun overlayWithLocationDoesNotEscalate() {
        val facts = app(allowed = setOf(PermissionCategory.LOCATION), special = setOf(SpecialAccess.OVERLAY))
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun requestedButNotAllowedDoesNotCountTowardCombination() {
        val facts = app(allowed = setOf(PermissionCategory.CAMERA)).copy(
            requested = setOf(PermissionCategory.CAMERA, PermissionCategory.MICROPHONE, PermissionCategory.CONTACTS),
        )
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun systemAppsAreNotEscalatedAboveSensitive() {
        val facts = app(
            isSystem = true,
            allowed = setOf(PermissionCategory.MICROPHONE, PermissionCategory.CAMERA, PermissionCategory.CONTACTS),
        )
        assertEquals(Severity.MEDIUM, Risk.level(facts))
    }

    @Test
    fun unreadableAppIsHigh() {
        val facts = app().copy(unreadable = true)
        assertEquals(Severity.HIGH, Risk.level(facts))
        assertEquals(R.string.reason_unreadable, Risk.findings(facts).first().reason)
    }

    @Test
    fun unknownSpecialAccessIsSensitiveAndDoesNotCountTowardCombination() {
        val unknownOnly = app().copy(unknownSpecial = setOf(SpecialAccess.OVERLAY))
        assertEquals(Severity.MEDIUM, Risk.level(unknownOnly))
        val withTwoOthers = app(allowed = setOf(PermissionCategory.CAMERA, PermissionCategory.LOCATION))
            .copy(unknownSpecial = setOf(SpecialAccess.USAGE))
        assertEquals(Severity.MEDIUM, Risk.level(withTwoOthers))
    }

    @Test
    fun approvedVpnIsHigh() {
        assertEquals(Severity.HIGH, Risk.level(app(special = setOf(SpecialAccess.VPN))))
    }

    @Test
    fun vpnServiceNotApprovedDoesNotCount() {
        assertNull(Risk.level(app().copy(requestedSpecial = setOf(SpecialAccess.VPN))))
    }

    @Test
    fun writeSettingsIsSensitive() {
        assertEquals(Severity.MEDIUM, Risk.level(app(special = setOf(SpecialAccess.WRITE_SETTINGS))))
    }

    @Test
    fun everySpecialAccessProducesAFinding() {
        for (access in SpecialAccess.entries) {
            assertEquals(access.name, 1, Risk.findings(app(special = setOf(access))).size)
        }
    }

    @Test
    fun healthPermissionsMapToHealth() {
        assertEquals(PermissionCategory.HEALTH, PermissionCategory.of("android.permission.health.READ_HEART_RATE"))
        assertEquals(Severity.MEDIUM, Risk.level(app(allowed = setOf(PermissionCategory.HEALTH))))
    }

    @Test
    fun voicemailUsesTheRealPermissionName() {
        assertEquals(PermissionCategory.PHONE, PermissionCategory.of("com.android.voicemail.permission.ADD_VOICEMAIL"))
        assertNull(PermissionCategory.of("android.permission.ADD_VOICEMAIL"))
    }

    @Test
    fun mergeTakesTheUnionOfSiblings() {
        val own = app(allowed = setOf(PermissionCategory.CAMERA))
        val sibling = app(
            allowed = setOf(PermissionCategory.SMS),
            special = setOf(SpecialAccess.ACCESSIBILITY),
        ).copy(packageName = "com.example.helper", unknownSpecial = setOf(SpecialAccess.USAGE))
        val merged = own.mergedWith(listOf(sibling))
        assertEquals(setOf(PermissionCategory.CAMERA, PermissionCategory.SMS), merged.allowed)
        assertEquals(setOf(PermissionCategory.CAMERA, PermissionCategory.SMS), merged.requested)
        assertEquals(setOf(SpecialAccess.ACCESSIBILITY), merged.special)
        assertEquals(setOf(SpecialAccess.USAGE), merged.unknownSpecial)
        assertEquals(listOf("com.example.helper"), merged.sharedWith)
        assertEquals("com.example", merged.packageName)
        assertEquals(Severity.HIGH, Risk.level(merged))
    }

    @Test
    fun mergeCarriesUnreadable() {
        val sibling = app().copy(packageName = "com.example.helper", unreadable = true)
        assertTrue(app().mergedWith(listOf(sibling)).unreadable)
    }

    @Test
    fun cleanedLabelStillMatchesUtilityRule() {
        assertTrue(Risk.isUtility(Text.cleanLabel("Flash​light", "com.example")))
    }

    @Test
    fun categoriesFollowAndroidVersions() {
        assertFalse(PermissionCategory.NOTIFICATIONS in PermissionCategory.availableOn(31))
        assertTrue(PermissionCategory.NOTIFICATIONS in PermissionCategory.availableOn(33))
        assertFalse(PermissionCategory.NEARBY in PermissionCategory.availableOn(30))
        assertTrue(PermissionCategory.CAMERA in PermissionCategory.availableOn(29))
    }

    @Test
    fun everyCatalogPermissionMapsBackToItsCategory() {
        for (category in PermissionCategory.entries) {
            for (permission in category.permissions) {
                assertEquals(category, PermissionCategory.of(permission))
            }
        }
        assertNull(PermissionCategory.of("android.permission.INTERNET"))
    }
}
