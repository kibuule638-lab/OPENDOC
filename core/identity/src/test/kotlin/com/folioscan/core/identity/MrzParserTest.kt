package com.folioscan.core.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MrzParserTest {

    // Official ICAO Doc 9303 Part 4 TD3 Specimen
    private val specimenLine1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val specimenLine2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"

    @Test
    fun `parses valid ICAO 9303 TD3 specimen with all check digits passing`() {
        val parsed = MrzParser.parseLines(specimenLine1, specimenLine2)
        assertNotNull(parsed)
        parsed!!
        assertEquals("P", parsed.documentCode)
        assertEquals("UTO", parsed.issuingCountry)
        assertEquals("ERIKSSON", parsed.surname)
        assertEquals("ANNA MARIA", parsed.givenNames)
        assertEquals("L898902C3", parsed.documentNumber)
        assertEquals("UTO", parsed.nationality)
        assertEquals("740812", parsed.dateOfBirth)
        assertEquals("F", parsed.sex)
        assertEquals("120415", parsed.expiryDate)
        assertTrue(parsed.documentNumberValid)
        assertTrue(parsed.dobValid)
        assertTrue(parsed.expiryValid)
        assertTrue(parsed.compositeValid)
        assertTrue(parsed.allCheckDigitsValid)
    }

    @Test
    fun `detects corrupted document number check digit`() {
        val badDocCheck = specimenLine2.replaceRange(9, 10, "9")
        val parsed = MrzParser.parseLines(specimenLine1, badDocCheck)!!
        assertFalse(parsed.documentNumberValid)
        assertFalse(parsed.allCheckDigitsValid)
    }

    @Test
    fun `detects corrupted DOB check digit`() {
        val badDobCheck = specimenLine2.replaceRange(19, 20, "0")
        val parsed = MrzParser.parseLines(specimenLine1, badDobCheck)!!
        assertFalse(parsed.dobValid)
        assertFalse(parsed.allCheckDigitsValid)
    }

    @Test
    fun `detects corrupted expiry check digit`() {
        val badExpCheck = specimenLine2.replaceRange(27, 28, "1")
        val parsed = MrzParser.parseLines(specimenLine1, badExpCheck)!!
        assertFalse(parsed.expiryValid)
        assertFalse(parsed.allCheckDigitsValid)
    }

    @Test
    fun `detects corrupted composite check digit`() {
        val badComposite = specimenLine2.replaceRange(43, 44, "9")
        val parsed = MrzParser.parseLines(specimenLine1, badComposite)!!
        assertTrue(parsed.documentNumberValid)
        assertTrue(parsed.dobValid)
        assertTrue(parsed.expiryValid)
        assertFalse(parsed.compositeValid)
        assertFalse(parsed.allCheckDigitsValid)
    }

    @Test
    fun `extracts MRZ lines from noisy multi-line OCR text`() {
        val ocrText = """
            PASSPORT / PASSEPORT
            Surname: ERIKSSON
            $specimenLine1
            $specimenLine2
        """.trimIndent()
        val parsed = MrzParser.parseFromOcrText(ocrText)
        assertNotNull(parsed)
        assertTrue(parsed!!.allCheckDigitsValid)
    }
}
