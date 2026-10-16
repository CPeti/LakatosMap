package com.pczumbel.navigator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a saved copy of the real register page for Almási-zsomboly (5452-2). */
class CadastreParseTest {
    private val html = javaClass.getResource("/cadastre_5452-2.html")!!.readText(Charsets.UTF_8)
    private val entry = Cadastre.parse(html, "5452-2", "url", 0L)!!
    private fun field(label: String) = entry.fields.firstOrNull { it.label == label }?.value

    @Test fun name() = assertEquals("Almási-zsomboly", entry.name)
    @Test fun cadastralNumber() = assertEquals("5452-2", field("Kataszteri szám"))
    @Test fun lengthAndDepth() {
        assertEquals("358 méter", field("Hossz"))
        assertEquals("100 méter", field("Mélység"))
    }
    @Test fun settlementAndProtection() {
        assertEquals("Bódvaszilas", field("Település"))
        assertEquals("fokozottan védett", field("Védettség"))
        assertEquals("Aggteleki NP Igazgatóság", field("NP Igazgatóság"))
    }
    @Test fun visitingRulesSkipNotApplicable() {
        assertEquals(listOf("Maximális csoportlétszám" to "8", "Napi maximális csoportszám" to "3"),
            entry.visiting.map { it.label to it.value })
    }
    @Test fun descriptionIsTheHistoryParagraph() {
        assertNotNull(entry.description)
        assertTrue(entry.description!!.startsWith("1982 óta fokozottan védett barlang"))
    }
    @Test fun galleryImages() {
        assertEquals(5, entry.images.size)
        assertTrue(entry.images.all { it.startsWith("https://termeszetvedelem.hu/_user/cave_images/5452-2_") && "thumbnails" !in it })
    }
    @Test fun unknownPageReturnsNull() = assertNull(Cadastre.parse("<html><body>Nincs találat</body></html>", "x", "u", 0))
}
