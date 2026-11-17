package com.dannycom888.lrcnotepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TextCodecTest {

    @Test
    fun roundTrip_chineseAndLrcTags_unchanged() {
        val original = "[00:00.00]陈楚生 - 有没有人告诉你\n" +
            "[00:31.02]<00:31.02>无<00:31.50>聊<00:31.90>望着夜空\n" +
            "[01:02.50]心太软 — 繁體字與中文標點，。！\n"
        val decoded = TextCodec.decode(TextCodec.encode(original))
        assertEquals("UTF-8", decoded.charsetName)
        assertEquals(original, decoded.text)
        // And a second round-trip is byte-identical.
        assertTrue(TextCodec.encode(original).contentEquals(TextCodec.encode(decoded.text)))
    }

    @Test
    fun decode_gbkBytes_fallsBackToGbk() {
        val chinese = "[00:00.00]任贤齐 - 心太软\n[00:12.00]把所有问题都自己扛\n"
        val gbkBytes = chinese.toByteArray(charset("GBK"))
        // Sanity: these GBK bytes must NOT be valid strict UTF-8 for this test to mean anything.
        val decoded = TextCodec.decode(gbkBytes)
        assertEquals("GBK", decoded.charsetName)
        assertEquals(chinese, decoded.text)
    }

    @Test
    fun decode_validUtf8_chinese_notMislabeledGbk() {
        val chinese = "简体中文歌词，繁體混排"
        val decoded = TextCodec.decode(chinese.toByteArray(StandardCharsets.UTF_8))
        assertEquals("UTF-8", decoded.charsetName)
        assertEquals(chinese, decoded.text)
    }

    @Test
    fun decode_utf8Bom_stripped() {
        val text = "[00:00.00]歌名\n正文"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(StandardCharsets.UTF_8)
        assertEquals(text, TextCodec.decode(withBom).text)
    }

    @Test
    fun decode_empty_isEmpty() {
        assertEquals("", TextCodec.decode(ByteArray(0)).text)
    }
}

class DocumentStateTest {

    @Test
    fun freshAndLoaded_areClean() {
        assertFalse(DocumentState().isDirty)
        val s = DocumentState()
        s.onLoaded("abc")
        assertFalse(s.isDirty)
    }

    @Test
    fun edit_makesDirty_saveMakesClean() {
        val s = DocumentState()
        s.onLoaded("原文")
        s.currentText = "原文改"
        assertTrue(s.isDirty)
        s.onSaved()
        assertFalse(s.isDirty)
        assertEquals("原文改", s.savedText)
    }

    @Test
    fun editBackToOriginal_isCleanAgain() {
        val s = DocumentState()
        s.onLoaded("[00:01.00]心太软")
        s.currentText = "别的"
        assertTrue(s.isDirty)
        s.currentText = "[00:01.00]心太软"
        assertFalse(s.isDirty)
    }

    @Test
    fun new_isCleanAndEmpty() {
        val s = DocumentState()
        s.onLoaded("有内容")
        s.currentText = "改了"
        s.onNew()
        assertFalse(s.isDirty)
        assertEquals("", s.currentText)
    }
}
