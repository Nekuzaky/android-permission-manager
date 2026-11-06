package dev.nekuz.permhub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    private val pkg = "com.example.app"

    @Test
    fun normalLabelIsUnchanged() {
        assertEquals("Google Maps", Text.cleanLabel("Google Maps", pkg))
        assertEquals("日本語 Café", Text.cleanLabel("日本語 Café", pkg))
    }

    @Test
    fun emojiIsKept() {
        assertEquals("Notes 📝", Text.cleanLabel("Notes 📝", pkg))
    }

    @Test
    fun nullLabelFallsBackToPackage() {
        assertEquals(pkg, Text.cleanLabel(null, pkg))
    }

    @Test
    fun bidiOverridesAreRemoved() {
        assertEquals("harmlessexe", Text.cleanLabel("harmless‮exe‬", pkg))
        assertEquals("abc", Text.cleanLabel("⁦a⁧b⁩c‏", pkg))
    }

    @Test
    fun zeroWidthAndFormatCharactersAreRemoved() {
        assertEquals("Calculator", Text.cleanLabel("Cal​cu‌la‍to﻿r­", pkg))
    }

    @Test
    fun controlCharactersAreRemoved() {
        assertEquals("ab", Text.cleanLabel("a\u0000\u0007\u0085b", pkg))
    }

    @Test
    fun newlinesBecomeASingleSpace() {
        assertEquals("Safe app", Text.cleanLabel("Safe\n\r\n  app", pkg))
        assertEquals("Safe app", Text.cleanLabel("Safe  app", pkg))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("App", Text.cleanLabel("  \n App \t ", pkg))
    }

    @Test
    fun blankLabelsFallBackToPackage() {
        assertEquals(pkg, Text.cleanLabel("", pkg))
        assertEquals(pkg, Text.cleanLabel("   \n\t", pkg))
        assertEquals(pkg, Text.cleanLabel("​​‮", pkg))
        assertEquals(pkg, Text.cleanLabel("ㅤ⠀ﾠ", pkg))
        assertEquals(pkg, Text.cleanLabel("́️", pkg))
    }

    @Test
    fun longLabelIsCapped() {
        val clean = Text.cleanLabel("a".repeat(100_000), pkg)
        assertEquals(Text.MAX_LABEL_LENGTH + 1, clean.length)
        assertTrue(clean.endsWith("…"))
    }

    @Test
    fun labelAtTheLimitIsNotMarkedTruncated() {
        val label = "a".repeat(Text.MAX_LABEL_LENGTH)
        assertEquals(label, Text.cleanLabel(label, pkg))
    }

    @Test
    fun capDoesNotSplitSurrogatePairs() {
        val clean = Text.cleanLabel("📝".repeat(500), pkg).removeSuffix("…")
        assertEquals(Text.MAX_LABEL_LENGTH, clean.codePointCount(0, clean.length))
        assertEquals(Text.MAX_LABEL_LENGTH * 2, clean.length)
    }

    @Test
    fun packagesAreReadFromComponentLists() {
        assertEquals(
            setOf("com.a", "com.b"),
            Text.packagesOf("com.a/.Service:com.b/com.b.Other:com.a/.Second"),
        )
    }

    @Test
    fun emptyOrMalformedComponentListsAreEmpty() {
        assertEquals(emptySet<String>(), Text.packagesOf(null))
        assertEquals(emptySet<String>(), Text.packagesOf(""))
        assertEquals(emptySet<String>(), Text.packagesOf("noslash:/.OnlyClass"))
    }

    @Test
    fun hexIsDecoded() {
        assertArrayEquals(byteArrayOf(0x5b, 0x33, 0x7c, 0xe0.toByte(), 0xff.toByte()), Text.hexToBytes("5b337ce0ff"))
    }
}
