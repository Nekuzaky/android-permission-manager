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
