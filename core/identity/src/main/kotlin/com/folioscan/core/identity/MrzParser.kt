package com.folioscan.core.identity

import com.folioscan.core.model.PassportMrzData

/**
 * ICAO 9303 TD3 (2 x 44 chars) Passport Machine-Readable Zone parser and check-digit validator.
 */
object MrzParser {
    private val WEIGHTS = intArrayOf(7, 3, 1)

    fun computeCheckDigit(field: String): Int {
        var sum = 0
        field.forEachIndexed { idx, ch ->
            val value = charValue(ch)
            sum += value * WEIGHTS[idx % 3]
        }
        return sum % 10
    }

    private fun charValue(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'A'..'Z' -> ch - 'A' + 10
        '<' -> 0
        else -> 0
    }

    /**
     * Attempts to locate two 44-character TD3 MRZ lines inside full OCR text and parse them.
     */
    fun parseFromOcrText(ocrText: String): PassportMrzData? {
        val candidateLines = ocrText
            .lineSequence()
            .map { line ->
                line.uppercase()
                    .replace(" ", "")
                    .replace("«", "<")
                    .replace("‹", "<")
                    .replace("(", "<")
                    .replace(")", "<")
                    .filter { it in 'A'..'Z' || it in '0'..'9' || it == '<' }
            }
            .filter { it.length >= 38 }
            .toList()

        if (candidateLines.size < 2) return null

        // Look for adjacent pair where line 1 starts with P
        for (i in 0 until candidateLines.size - 1) {
            val l1 = normalizeLineLength(candidateLines[i])
            val l2 = normalizeLineLength(candidateLines[i + 1])
            if (l1.startsWith("P")) {
                return parseLines(l1, l2)
            }
        }
        return parseLines(
            normalizeLineLength(candidateLines[candidateLines.size - 2]),
            normalizeLineLength(candidateLines.last())
        )
    }

    private fun normalizeLineLength(line: String): String {
        return when {
            line.length == 44 -> line
            line.length > 44 -> line.take(44)
            else -> line.padEnd(44, '<')
        }
    }

    fun parseLines(rawLine1: String, rawLine2: String): PassportMrzData? {
        val line1 = normalizeLineLength(rawLine1.uppercase().replace(" ", ""))
        val line2 = normalizeLineLength(rawLine2.uppercase().replace(" ", ""))
        if (line1.length != 44 || line2.length != 44) return null

        val docCode = line1.substring(0, 2).replace("<", "")
        val issuingCountry = cleanAlphaField(line1.substring(2, 5))
        val nameBlock = line1.substring(5, 44)
        val nameParts = nameBlock.split("<<", limit = 2)
        val surname = nameParts.getOrElse(0) { "" }.replace("<", " ").trim()
        val givenNames = nameParts.getOrElse(1) { "" }.replace("<", " ").replace(Regex("\\s+"), " ").trim()

        val docNumberRaw = line2.substring(0, 9)
        val docNumberCheck = normalizeDigitChar(line2[9])
        val docNumber = docNumberRaw.replace("<", "").trim()
        val docNumValid = docNumberCheck.isDigit() &&
            computeCheckDigit(docNumberRaw) == (docNumberCheck - '0')

        val nationality = cleanAlphaField(line2.substring(10, 13))

        val dobRaw = normalizeDigitField(line2.substring(13, 19))
        val dobCheck = normalizeDigitChar(line2[19])
        val dobValid = dobCheck.isDigit() &&
            dobRaw.all { it.isDigit() } &&
            computeCheckDigit(dobRaw) == (dobCheck - '0')

        val sex = line2[20].toString()

        val expiryRaw = normalizeDigitField(line2.substring(21, 27))
        val expiryCheck = normalizeDigitChar(line2[27])
        val expiryValid = expiryCheck.isDigit() &&
            expiryRaw.all { it.isDigit() } &&
            computeCheckDigit(expiryRaw) == (expiryCheck - '0')

        // ICAO 9303 TD3 composite check digit covers:
        // docNumber (0..9) + dob (13..19) + expiry (21..42)
        val compositeInput = line2.substring(0, 10) +
            dobRaw + dobCheck +
            expiryRaw + expiryCheck +
            line2.substring(28, 43)
        val compositeCheck = normalizeDigitChar(line2[43])
        val compositeValid = compositeCheck.isDigit() &&
            computeCheckDigit(compositeInput) == (compositeCheck - '0')

        return PassportMrzData(
            documentCode = docCode.ifEmpty { "P" },
            issuingCountry = issuingCountry,
            surname = surname,
            givenNames = givenNames,
            documentNumber = docNumber,
            documentNumberValid = docNumValid,
            nationality = nationality,
            dateOfBirth = dobRaw,
            dobValid = dobValid,
            sex = sex,
            expiryDate = expiryRaw,
            expiryValid = expiryValid,
            compositeValid = compositeValid,
            rawLine1 = line1,
            rawLine2 = line2
        )
    }

    private fun cleanAlphaField(raw: String): String {
        return raw.replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
            .replace('8', 'B')
            .replace("<", "")
    }

    private fun normalizeDigitChar(ch: Char): Char = when (ch) {
        'O', 'D', 'Q' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'B' -> '8'
        else -> ch
    }

    private fun normalizeDigitField(raw: String): String =
        raw.map { normalizeDigitChar(it) }.joinToString("")
}
