package com.folioscan.core.identity

import android.content.Intent
import android.provider.ContactsContract
import com.folioscan.core.model.BusinessCardData

/**
 * Offline regex + heuristic parser for business card OCR text and ContactsContract intent builder.
 */
object BusinessCardParser {
    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[\\s.\\-]?)?(?:\\(?\\d{2,4}\\)?[\\s.\\-]?)\\d{3,4}[\\s.\\-]?\\d{3,4}")
    private val URL_REGEX = Regex("(?:https?://)?(?:www\\.)?[A-Za-z0-9\\-]+\\.[A-Za-z]{2,}(?:/[^\\s]*)?")

    private val TITLE_KEYWORDS = listOf(
        "ceo", "cto", "cfo", "coo", "vp", "director", "manager", "engineer",
        "developer", "designer", "consultant", "founder", "architect", "lead",
        "specialist", "analyst", "president", "partner", "officer"
    )

    private val COMPANY_SUFFIXES = listOf(
        "inc", "llc", "ltd", "corp", "gmbh", "ag", "sa", "pty", "group",
        "labs", "studio", "studios", "technologies", "solutions", "systems"
    )

    fun parse(ocrText: String): BusinessCardData {
        val lines = ocrText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val emails = EMAIL_REGEX.findAll(ocrText).map { it.value }.distinct().toList()
        val phones = PHONE_REGEX.findAll(ocrText)
            .map { it.value.trim() }
            .filter { it.count(Char::isDigit) >= 7 }
            .distinct()
            .toList()

        val textWithoutEmails = EMAIL_REGEX.replace(ocrText, " ")
        val website = URL_REGEX.findAll(textWithoutEmails)
            .map { it.value }
            .firstOrNull { candidate ->
                candidate.startsWith("http", ignoreCase = true) ||
                    candidate.startsWith("www.", ignoreCase = true) ||
                    Regex("\\.[A-Za-z]{2,6}(?:/.*)?$").containsMatchIn(candidate)
            } ?: ""

        val remainingLines = lines.filter { line ->
            !EMAIL_REGEX.containsMatchIn(line) &&
                phones.none { line.contains(it) } &&
                (website.isEmpty() || !line.contains(website))
        }

        val jobTitle = remainingLines.firstOrNull { line ->
            val lower = line.lowercase()
            TITLE_KEYWORDS.any { kw -> Regex("\\b$kw\\b").containsMatchIn(lower) }
        } ?: ""

        val companyFromSuffix = remainingLines.firstOrNull { line ->
            val lower = line.lowercase()
            line != jobTitle && COMPANY_SUFFIXES.any { s -> Regex("\\b$s\\b").containsMatchIn(lower) }
        }
        val companyFromDomain = emails.firstOrNull()
            ?.substringAfter("@")
            ?.substringBefore(".")
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()

        val fullName = remainingLines.firstOrNull { line ->
            line != jobTitle &&
                line != companyFromSuffix &&
                line.count(Char::isDigit) == 0 &&
                line.split(Regex("\\s+")).size in 2..4 &&
                line.length <= 40
        } ?: remainingLines.firstOrNull { it != jobTitle && it != companyFromSuffix } ?: ""

        val company = companyFromSuffix
            ?: remainingLines.firstOrNull { line ->
                line != fullName && line != jobTitle && line.count(Char::isDigit) == 0
            }
            ?: companyFromDomain

        val address = remainingLines.filter { line ->
            line != fullName && line != jobTitle && line != company && line.any(Char::isDigit)
        }.joinToString(", ")

        return BusinessCardData(
            fullName = fullName,
            jobTitle = jobTitle,
            company = company,
            phones = phones,
            emails = emails,
            website = website,
            address = address,
            rawText = ocrText
        )
    }

    /**
     * Builds an Android ContactsContract INSERT intent requiring zero runtime permissions.
     */
    fun buildAddToContactsIntent(
        name: String,
        company: String,
        jobTitle: String,
        phone: String,
        email: String
    ): Intent {
        return Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (company.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, company)
            if (jobTitle.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, jobTitle)
            if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
        }
    }
}
