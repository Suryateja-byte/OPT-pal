package com.sidekick.opt_pal.feature.tax

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.data.model.W2StateWageRow
import java.util.Locale

fun DocumentMetadata.toW2ExtractionDraft(): W2ExtractionDraft? {
    if (DocumentProcessingMode.fromWireValue(processingMode) != DocumentProcessingMode.ANALYZE) return null
    if (processingStatus != "processed") return null
    val normalizedIndex = extractedData.orEmpty().mapKeys { it.key.normalizeTaxKey() }
    val typeLabel = "${documentType.normalizeTaxKey()} ${userTag.normalizeTaxKey()} ${fileName.normalizeTaxKey()}"
    if (!typeLabel.contains("w2") && normalizedIndex["documenttype"]?.toString()?.normalizeTaxKey() != "w2") {
        return null
    }
    return W2ExtractionDraft(
        documentId = id,
        fileName = fileName,
        displayName = userTag.ifBlank { fileName },
        taxYear = normalizedIndex.firstValue("taxyear", "year", "w2year").toTaxYear(),
        employerName = normalizedIndex.firstValue("employername", "employer").asTrimmedString(),
        employerEinMasked = normalizedIndex.firstValue("employereinmasked", "employerein", "ein").asMaskedEin().orEmpty(),
        employeeName = normalizedIndex.firstValue("employeename", "name").asTrimmedString(),
        employeeSsnLast4 = normalizedIndex.firstValue("employeessnlast4", "employeessn", "ssn").asLast4().orEmpty(),
        wagesBox1 = normalizedIndex.firstValue("wagesbox1", "box1", "box_1", "wages").asMoney(),
        federalWithholdingBox2 = normalizedIndex.firstValue("federalwithholdingbox2", "box2", "box_2").asMoney(),
        socialSecurityWagesBox3 = normalizedIndex.firstValue("socialsecuritywagesbox3", "box3", "box_3").asMoney(),
        socialSecurityTaxBox4 = normalizedIndex.firstValue("socialsecuritytaxbox4", "box4", "box_4").asMoney(),
        medicareWagesBox5 = normalizedIndex.firstValue("medicarewagesbox5", "box5", "box_5").asMoney(),
        medicareTaxBox6 = normalizedIndex.firstValue("medicaretaxbox6", "box6", "box_6").asMoney(),
        stateWageRows = normalizedIndex.firstValue("statewagerows", "staterows").asStateRows()
    )
}

private fun String.normalizeTaxKey(): String = lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

private fun Any?.asTrimmedString(): String = toString().trim().takeIf { it.isNotBlank() }.orEmpty()

private fun Any?.asMoney(): Double? {
    val raw = when (this) {
        null -> return null
        is Number -> return ((toDouble() * 100).toLong() / 100.0)
        else -> toString().replace("$", "").replace(",", "").trim()
    }
    return raw.toDoubleOrNull()?.let { ((it * 100).toLong() / 100.0) }
}

private fun Any?.asLast4(): String? {
    val digits = toString().filter(Char::isDigit)
    return if (digits.length >= 4) digits.takeLast(4) else null
}

private fun Any?.asMaskedEin(): String? {
    val digits = toString().filter(Char::isDigit)
    return if (digits.length >= 9) "XX-XXX${digits.takeLast(4)}" else null
}

private fun Any?.toTaxYear(): Int? {
    return when (this) {
        is Number -> toInt().takeIf { it in 1900..2500 }
        else -> toString().trim().toIntOrNull()?.takeIf { it in 1900..2500 }
    }
}

private fun Map<String, Any>.firstValue(vararg keys: String): Any? {
    return keys.firstNotNullOfOrNull { this[it] }
}

private fun Any?.asStateRows(): List<W2StateWageRow> {
    val rows = this as? List<*> ?: return emptyList()
    return rows.mapNotNull { raw ->
        val row = raw as? Map<*, *> ?: return@mapNotNull null
        val stateCode = row["stateCode"]?.toString().orEmpty().ifBlank {
            row["state_code"]?.toString().orEmpty()
        }
        W2StateWageRow(
            stateCode = stateCode,
            wages = row["wages"].asMoney(),
            withholding = row["withholding"].asMoney()
        )
    }
}
