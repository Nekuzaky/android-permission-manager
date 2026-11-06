package dev.nekuz.permhub

object Text {
    const val MAX_LABEL_LENGTH = 100

    private val INVISIBLE_CODE_POINTS = setOf(0x034F, 0x115F, 0x1160, 0x17B4, 0x17B5, 0x2800, 0x3164, 0xFFA0)

    fun cleanLabel(raw: String?, packageName: String): String {
        if (raw == null) return packageName
        val clean = StringBuilder()
        var length = 0
        var truncated = false
        var hasVisible = false
        var pendingSpace = false
        var index = 0
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            index += Character.charCount(codePoint)
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = length > 0
                continue
            }
            if (isRemoved(codePoint)) continue
            if (length >= MAX_LABEL_LENGTH) {
                truncated = true
                break
            }
            if (pendingSpace) {
                clean.append(' ')
                length++
                pendingSpace = false
            }
            clean.appendCodePoint(codePoint)
            length++
            hasVisible = hasVisible || isVisible(codePoint)
        }
        if (!hasVisible) return packageName
        return if (truncated) "${clean.toString().trimEnd()}…" else clean.toString()
    }

    fun packagesOf(components: String?): Set<String> =
        components.orEmpty()
            .split(':')
            .map { it.substringBefore('/', "").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun isRemoved(codePoint: Int): Boolean {
        if (codePoint in INVISIBLE_CODE_POINTS) return true
        return when (Character.getType(codePoint).toByte()) {
            Character.CONTROL,
            Character.FORMAT,
            Character.SURROGATE,
            Character.PRIVATE_USE,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR -> true
            else -> false
        }
    }

    private fun isVisible(codePoint: Int): Boolean =
        when (Character.getType(codePoint).toByte()) {
            Character.NON_SPACING_MARK,
            Character.ENCLOSING_MARK,
            Character.COMBINING_SPACING_MARK -> false
            else -> true
        }
}
