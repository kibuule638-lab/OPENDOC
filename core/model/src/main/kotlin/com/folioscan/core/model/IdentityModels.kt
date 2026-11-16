package com.folioscan.core.model

import kotlinx.serialization.Serializable

@Serializable
data class IdentityField(
    val key: String,
    val label: String,
    val value: String,
    val isValid: Boolean = true,
    val validationNote: String? = null
)

@Serializable
data class IdentityData(
    val documentId: String,
    val fields: List<IdentityField>,
    val mrzRaw: String? = null,
    val mrzValid: Boolean = false
)

@Serializable
data class PassportMrzData(
    val documentCode: String,
    val issuingCountry: String,
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val documentNumberValid: Boolean,
    val nationality: String,
    val dateOfBirth: String,
    val dobValid: Boolean,
    val sex: String,
    val expiryDate: String,
    val expiryValid: Boolean,
    val compositeValid: Boolean,
    val rawLine1: String,
    val rawLine2: String
) {
    val allCheckDigitsValid: Boolean
        get() = documentNumberValid && dobValid && expiryValid && compositeValid

    fun toIdentityFields(): List<IdentityField> = listOf(
        IdentityField("surname", "Surname", surname, surname.isNotBlank(), if (surname.isBlank()) "Missing surname" else null),
        IdentityField("given_names", "Given Names", givenNames, givenNames.isNotBlank(), if (givenNames.isBlank()) "Missing given names" else null),
        IdentityField("document_number", "Document No.", documentNumber, documentNumberValid, if (!documentNumberValid) "MRZ check digit mismatch" else null),
        IdentityField("nationality", "Nationality", nationality, nationality.length == 3, if (nationality.length != 3) "Expected 3-letter ICAO code" else null),
        IdentityField("dob", "Date of Birth (YYMMDD)", dateOfBirth, dobValid, if (!dobValid) "DOB check digit mismatch" else null),
        IdentityField("sex", "Sex", sex, sex in setOf("M", "F", "<", "X"), null),
        IdentityField("expiry", "Expiry Date (YYMMDD)", expiryDate, expiryValid, if (!expiryValid) "Expiry check digit mismatch" else null),
        IdentityField("issuing_country", "Issuing State", issuingCountry, issuingCountry.length == 3, null),
        IdentityField("composite", "Composite Check", if (compositeValid) "PASS" else "FAIL", compositeValid, if (!compositeValid) "Composite MRZ check digit mismatch" else null)
    )
}

@Serializable
data class BusinessCardData(
    val fullName: String,
    val jobTitle: String,
    val company: String,
    val phones: List<String>,
    val emails: List<String>,
    val website: String,
    val address: String,
    val rawText: String
) {
    fun toIdentityFields(): List<IdentityField> = listOf(
        IdentityField("name", "Full Name", fullName, fullName.isNotBlank(), if (fullName.isBlank()) "Could not infer name" else null),
        IdentityField("job_title", "Title", jobTitle, true, null),
        IdentityField("company", "Company", company, company.isNotBlank(), if (company.isBlank()) "Optional company" else null),
        IdentityField("phone", "Phone", phones.joinToString(", "), phones.isNotEmpty(), if (phones.isEmpty()) "No phone number found" else null),
        IdentityField("email", "Email", emails.joinToString(", "), emails.isNotEmpty(), if (emails.isEmpty()) "No email found" else null),
        IdentityField("website", "Website", website, true, null),
        IdentityField("address", "Address", address, true, null)
    )
}
