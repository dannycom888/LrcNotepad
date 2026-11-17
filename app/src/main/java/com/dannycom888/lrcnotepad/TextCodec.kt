package com.dannycom888.lrcnotepad

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Text decoding for .lrc files: strict UTF-8 first, GBK fallback when the
 * bytes are not valid UTF-8 (older Chinese lyric files are often GBK).
 * Saving always writes UTF-8, with no BOM, preserving the text exactly.
 */
object TextCodec {

    data class Decoded(val text: String, val charsetName: String)

    fun decode(bytes: ByteArray): Decoded {
        // Strip a UTF-8 BOM if present; it is an encoding marker, not content.
        var offset = 0
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            offset = 3
        }
        val slice = if (offset == 0) bytes else bytes.copyOfRange(offset, bytes.size)
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            Decoded(decoder.decode(ByteBuffer.wrap(slice)).toString(), "UTF-8")
        } catch (e: CharacterCodingException) {
            val gbk = charset("GBK")
            Decoded(String(slice, gbk), "GBK")
        }
    }

    fun encode(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)
}

/** Tracks unsaved-changes state for one document, independent of Android UI. */
class DocumentState(initialText: String = "") {
    var savedText: String = initialText
        private set
    var currentText: String = initialText

    val isDirty: Boolean get() = currentText != savedText

    fun onLoaded(text: String) {
        savedText = text
        currentText = text
    }

    fun onSaved() {
        savedText = currentText
    }

    fun onNew() {
        savedText = ""
        currentText = ""
    }
}
