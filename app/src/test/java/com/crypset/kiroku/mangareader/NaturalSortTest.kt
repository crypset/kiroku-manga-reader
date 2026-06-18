package com.crypset.kiroku.mangareader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {
    @Test
    fun naturalSortPlacesNumericPartsInNumericOrder() {
        val sorted = listOf("chapter 10", "chapter 2", "chapter 1")
            .sortedWith(NaturalSort::compare)

        assertEquals(listOf("chapter 1", "chapter 2", "chapter 10"), sorted)
    }

    @Test
    fun imageFileTypeAcceptsSupportedExtensionsCaseInsensitively() {
        assertTrue(ImageFileType.isSupported("001.JPG"))
        assertTrue(ImageFileType.isSupported("cover.webp"))
        assertFalse(ImageFileType.isSupported("notes.txt"))
        assertFalse(ImageFileType.isSupported("archive.zip"))
    }
}
