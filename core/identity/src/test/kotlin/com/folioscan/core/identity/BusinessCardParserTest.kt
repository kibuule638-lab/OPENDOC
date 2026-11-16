package com.folioscan.core.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BusinessCardParserTest {

    @Test
    fun `extracts name title company phone email website from business card OCR`() {
        val raw = """
            Elena Vance
            Principal Systems Engineer
            Northstar Robotics Labs
            +1 (415) 555-0194
            elena.vance@northstar-labs.io
            https://northstar-labs.io
            420 Market St, San Francisco, CA
        """.trimIndent()

        val parsed = BusinessCardParser.parse(raw)
        assertEquals("Elena Vance", parsed.fullName)
        assertEquals("Principal Systems Engineer", parsed.jobTitle)
        assertEquals("Northstar Robotics Labs", parsed.company)
        assertTrue(parsed.emails.contains("elena.vance@northstar-labs.io"))
        assertTrue(parsed.phones.any { it.contains("415") })
        assertTrue(parsed.website.contains("northstar-labs.io"))
    }
}
