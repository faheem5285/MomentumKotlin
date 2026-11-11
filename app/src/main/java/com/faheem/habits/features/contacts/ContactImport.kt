package com.faheem.habits.features.contacts

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class ImportedContact(
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val email: String = "",
    val address: String = "",
    val notes: String = ""
)

internal fun parseContacts(input: InputStream, fileName: String): List<ImportedContact> {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension == "vcf" || extension == "vcard") {
        return parseVCard(input.bufferedReader().use { it.readText() })
    }
    val rows = when (extension) {
        "csv" -> parseCsv(input.bufferedReader().use { it.readText() })
        "xlsx" -> parseXlsx(input)
        "xls" -> error("Legacy .xls files are not supported. Save the workbook as .xlsx or CSV and import it again.")
        else -> error("Choose a VCF, CSV, or Excel .xlsx file.")
    }
    require(rows.isNotEmpty()) { "The selected file has no contact rows." }

    val headers = rows.first().map(::normalizeHeader)
    val nameIndex = headers.indexOfFirst { it in setOf("name", "fullname", "contactname") }
    require(nameIndex >= 0) { "The first row must contain a Name or Full Name column." }
    fun indexOf(vararg names: String) = headers.indexOfFirst { it in names }
    val phoneIndex = indexOf("phone", "mobile", "phonenumber", "mobilephone")
    val emailIndex = indexOf("email", "emailaddress")
    val addressIndex = indexOf("address", "streetaddress")
    val notesIndex = indexOf("notes", "note", "description")

    return rows.drop(1).mapNotNull { row ->
        val name = row.getOrNull(nameIndex).orEmpty().trim()
        if (name.isBlank()) null else ImportedContact(
            name = name,
            phoneNumbers = row.getOrNull(phoneIndex).orEmpty().split(Regex("[\\r\\n|]+"))
                .map(String::trim).filter(String::isNotBlank),
            email = row.getOrNull(emailIndex).orEmpty().trim(),
            address = row.getOrNull(addressIndex).orEmpty().trim(),
            notes = row.getOrNull(notesIndex).orEmpty().trim()
        )
    }
}

private fun parseVCard(text: String): List<ImportedContact> {
    val unfolded = text.replace(Regex("\\r?\\n[ \\t]"), "")
    val contacts = mutableListOf<ImportedContact>()
    var name: String? = null
    var structuredName: String? = null
    val phones = mutableListOf<String>()
    val emails = mutableListOf<String>()
    val addresses = mutableListOf<String>()
    val notes = mutableListOf<String>()

    fun finishCard() {
        val resolvedName = name?.takeIf(String::isNotBlank)
            ?: structuredName?.takeIf(String::isNotBlank)
        if (resolvedName != null) {
            contacts += ImportedContact(
                name = resolvedName,
                phoneNumbers = phones.distinct(),
                email = emails.distinct().joinToString("; "),
                address = addresses.distinct().joinToString("; "),
                notes = notes.distinct().joinToString("\n")
            )
        }
        name = null
        structuredName = null
        phones.clear()
        emails.clear()
        addresses.clear()
        notes.clear()
    }

    unfolded.lineSequence().forEach { line ->
        when {
            line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                name = null
                structuredName = null
                phones.clear()
                emails.clear()
                addresses.clear()
                notes.clear()
            }
            line.equals("END:VCARD", ignoreCase = true) -> finishCard()
            else -> {
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val property = line.substring(0, separator).substringAfterLast('.')
                    .substringBefore(';').uppercase()
                val value = unescapeVCard(line.substring(separator + 1).trim())
                when (property) {
                    "FN" -> name = value
                    "N" -> {
                        val parts = value.split(';')
                        structuredName = listOf(3, 1, 2, 0, 4)
                            .mapNotNull { parts.getOrNull(it)?.takeIf(String::isNotBlank) }
                            .joinToString(" ")
                    }
                    "TEL" -> if (value.isNotBlank()) phones += value
                    "EMAIL" -> if (value.isNotBlank()) emails += value
                    "ADR" -> value.split(';').filter(String::isNotBlank).joinToString(", ")
                        .takeIf(String::isNotBlank)?.let(addresses::add)
                    "NOTE" -> if (value.isNotBlank()) notes += value
                }
            }
        }
    }
    require(contacts.isNotEmpty()) { "The selected VCF file contains no readable contacts." }
    return contacts
}

private fun unescapeVCard(value: String): String = value
    .replace("\\n", "\n", ignoreCase = true)
    .replace("\\,", ",")
    .replace("\\;", ";")
    .replace("\\\\", "\\")

private fun normalizeHeader(value: String): String = value.trim().lowercase()
    .filter(Char::isLetterOrDigit)

private fun parseCsv(text: String): List<List<String>> {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val delimiter = listOf(',', ';', '\t').maxByOrNull { candidate -> firstLine.count { it == candidate } } ?: ','
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val char = text[i]
        when {
            char == '"' && quoted && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            char == '"' -> quoted = !quoted
            char == delimiter && !quoted -> { row += cell.toString(); cell.clear() }
            (char == '\n' || char == '\r') && !quoted -> {
                if (char == '\r' && text.getOrNull(i + 1) == '\n') i++
                row += cell.toString(); cell.clear()
                if (row.any(String::isNotBlank)) rows += row
                row = mutableListOf()
            }
            else -> cell.append(char)
        }
        i++
    }
    row += cell.toString()
    if (row.any(String::isNotBlank)) rows += row
    return rows
}

private fun parseXlsx(input: InputStream): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") {
                entries[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
        }
    }
    val sheet = entries["xl/worksheets/sheet1.xml"]
        ?: error("The workbook does not contain a readable first worksheet.")
    val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
    val rows = mutableListOf<List<String>>()
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(sheet.inputStream(), "UTF-8")
    }
    var event = parser.eventType
    var currentRow: MutableMap<Int, String>? = null
    var column = 0
    var cellType = ""
    var cellValue = ""
    var inValue = false
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> currentRow = mutableMapOf()
                "c" -> {
                    val reference = parser.getAttributeValue(null, "r").orEmpty()
                    column = columnIndex(reference)
                    cellType = parser.getAttributeValue(null, "t").orEmpty()
                    cellValue = ""
                }
                "v" -> inValue = true
                "t" -> if (cellType == "inlineStr") inValue = true
            }
            XmlPullParser.TEXT -> if (inValue) cellValue += parser.text
            XmlPullParser.END_TAG -> when (parser.name) {
                "v", "t" -> inValue = false
                "c" -> {
                    val value = if (cellType == "s") shared.getOrNull(cellValue.toIntOrNull() ?: -1).orEmpty() else cellValue
                    currentRow?.set(column, value)
                }
                "row" -> {
                    val values = currentRow.orEmpty()
                    rows += (0..(values.keys.maxOrNull() ?: -1)).map { values[it].orEmpty() }
                    currentRow = null
                }
            }
        }
        event = parser.next()
    }
    return rows
}

private fun parseSharedStrings(xml: ByteArray): List<String> {
    val result = mutableListOf<String>()
    val parser = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
    var event = parser.eventType
    var inString = false
    var inText = false
    var text = StringBuilder()
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "si" -> { inString = true; text = StringBuilder() }
                "t" -> if (inString) inText = true
            }
            XmlPullParser.TEXT -> if (inString && inText) text.append(parser.text)
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inText = false
                "si" -> { result += text.toString(); inString = false }
            }
        }
        event = parser.next()
    }
    return result
}

private fun columnIndex(reference: String): Int {
    val letters = reference.takeWhile(Char::isLetter).uppercase()
    var index = 0
    letters.forEach { index = index * 26 + (it - 'A' + 1) }
    return (index - 1).coerceAtLeast(0)
}
