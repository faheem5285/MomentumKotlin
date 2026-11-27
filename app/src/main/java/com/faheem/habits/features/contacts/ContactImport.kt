package com.faheem.habits.features.contacts

import android.util.Xml
import com.faheem.habits.core.data.sync.MAX_FILE_TRANSFER_BYTES
import com.faheem.habits.core.data.sync.readBoundedFileBytes
import com.faheem.habits.core.data.sync.readBoundedText
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

internal const val MAX_CONTACT_IMPORT_ROWS = 5_000
internal const val MAX_CONTACT_IMPORT_COLUMNS = 32
internal const val MAX_CONTACT_FIELD_CHARS = 2_000

private const val MAX_CONTACT_PHONES_PER_CARD = 12
private const val MAX_VCARD_PROPERTIES_PER_CARD = 200
private const val MAX_WORKBOOK_ENTRIES = 200
private const val MAX_WORKBOOK_SHARED_STRINGS = MAX_CONTACT_IMPORT_ROWS * MAX_CONTACT_IMPORT_COLUMNS

private const val MALFORMED_VCF = "The selected VCF file is malformed."
private const val MALFORMED_CSV = "The selected CSV file is malformed."
private const val MALFORMED_XLSX = "The selected workbook is malformed."

internal data class ImportedContact(
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val email: String = "",
    val address: String = "",
    val notes: String = ""
)

internal fun parseContacts(input: InputStream, fileName: String): List<ImportedContact> {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "vcf", "vcard" -> parseVCard(input.readBoundedText("VCF file"))
        "csv" -> parseTabularContacts(parseCsv(input.readBoundedText("CSV file")))
        "xlsx" -> parseTabularContacts(parseXlsx(input))
        "xls" -> throw IllegalArgumentException("Legacy .xls files are not supported. Save the workbook as .xlsx or CSV.")
        else -> throw IllegalArgumentException("Choose a VCF, CSV, or Excel .xlsx file.")
    }
}

private fun parseTabularContacts(rows: List<List<String>>): List<ImportedContact> {
    require(rows.isNotEmpty()) { "The selected file has no contact rows." }
    val headers = rows.first().map(::normalizeHeader)
    require(headers.size <= MAX_CONTACT_IMPORT_COLUMNS) { "The selected file has too many columns." }
    val nameIndex = headers.indexOfFirst { it in setOf("name", "fullname", "contactname") }
    require(nameIndex >= 0) { "The first row must contain a Name or Full Name column." }
    fun indexOf(vararg names: String) = headers.indexOfFirst { it in names }
    val phoneIndex = indexOf("phone", "mobile", "phonenumber", "mobilephone")
    val emailIndex = indexOf("email", "emailaddress")
    val addressIndex = indexOf("address", "streetaddress")
    val notesIndex = indexOf("notes", "note", "description")

    val dataRows = rows.drop(1)
    require(dataRows.size <= MAX_CONTACT_IMPORT_ROWS) { "The selected file has too many contact rows." }
    return dataRows.mapNotNull { row ->
        require(row.size <= MAX_CONTACT_IMPORT_COLUMNS) { MALFORMED_CSV }
        val name = boundedField(row.getOrNull(nameIndex).orEmpty())
        if (name.isBlank()) {
            null
        } else {
            val numbers = row.getOrNull(phoneIndex).orEmpty()
                .split(Regex("[\\r\\n|;]+"))
                .map(::boundedField)
                .filter(String::isNotBlank)
                .distinct()
            require(numbers.size <= MAX_CONTACT_PHONES_PER_CARD) { "A contact has too many phone numbers." }
            ImportedContact(
                name = name,
                phoneNumbers = numbers,
                email = boundedField(row.getOrNull(emailIndex).orEmpty()),
                address = boundedField(row.getOrNull(addressIndex).orEmpty()),
                notes = boundedField(row.getOrNull(notesIndex).orEmpty())
            )
        }
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
    var inCard = false
    var sawCard = false
    var propertyCount = 0

    fun resetCard() {
        name = null
        structuredName = null
        phones.clear()
        emails.clear()
        addresses.clear()
        notes.clear()
        propertyCount = 0
    }

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
            require(contacts.size <= MAX_CONTACT_IMPORT_ROWS) { "The selected VCF file has too many contacts." }
        }
        resetCard()
        inCard = false
    }

    unfolded.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach
        val upper = line.uppercase()
        when {
            upper.startsWith("BEGIN:VCARD") -> {
                require(!inCard) { MALFORMED_VCF }
                sawCard = true
                inCard = true
                resetCard()
            }
            upper.startsWith("END:VCARD") -> {
                require(inCard) { MALFORMED_VCF }
                finishCard()
            }
            else -> {
                require(inCard) { MALFORMED_VCF }
                propertyCount++
                require(propertyCount <= MAX_VCARD_PROPERTIES_PER_CARD) { "A VCF contact has too many fields." }
                val separator = line.indexOf(':')
                require(separator > 0) { MALFORMED_VCF }
                val property = line.substring(0, separator).substringAfterLast('.')
                    .substringBefore(';').uppercase()
                val rawValue = line.substring(separator + 1).trim()
                require(rawValue.length <= MAX_CONTACT_FIELD_CHARS) { "A contact field is too long." }
                val value = boundedField(unescapeVCard(rawValue))
                when (property) {
                    "FN" -> name = value
                    "N" -> {
                        val parts = value.split(';')
                        structuredName = listOf(3, 1, 2, 0, 4)
                            .mapNotNull { parts.getOrNull(it)?.takeIf(String::isNotBlank) }
                            .joinToString(" ")
                    }
                    "TEL" -> {
                        if (value.isNotBlank()) {
                            require(phones.size < MAX_CONTACT_PHONES_PER_CARD) { "A contact has too many phone numbers." }
                            phones += value
                        }
                    }
                    "EMAIL" -> {
                        if (value.isNotBlank()) {
                            require(emails.size < MAX_CONTACT_PHONES_PER_CARD) { "A contact has too many email addresses." }
                            emails += value
                        }
                    }
                    "ADR" -> value.split(';').filter(String::isNotBlank).joinToString(", ")
                        .takeIf(String::isNotBlank)?.let(addresses::add)
                    "NOTE" -> if (value.isNotBlank()) notes += value
                }
            }
        }
    }
    require(sawCard && !inCard) { MALFORMED_VCF }
    require(contacts.isNotEmpty()) { "The selected VCF file contains no readable contacts." }
    return contacts
}

private fun unescapeVCard(value: String): String = value
    .replace("\\n", "\n", ignoreCase = true)
    .replace("\\,", ",")
    .replace("\\;", ";")
    .replace("\\\\", "\\")

private fun boundedField(value: String): String {
    require(value.length <= MAX_CONTACT_FIELD_CHARS) { "A contact field is too long." }
    return value.trim()
}

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
                row += cell.toString()
                addCsvRow(rows, row)
                row = mutableListOf()
                cell.clear()
            }
            else -> {
                cell.append(char)
                require(cell.length <= MAX_CONTACT_FIELD_CHARS) { "A contact field is too long." }
            }
        }
        i++
    }
    require(!quoted) { "The selected CSV file has an unclosed quoted field." }
    row += cell.toString()
    addCsvRow(rows, row)
    return rows
}

private fun addCsvRow(rows: MutableList<List<String>>, row: List<String>) {
    require(row.size <= MAX_CONTACT_IMPORT_COLUMNS) { MALFORMED_CSV }
    if (row.any(String::isNotBlank)) {
        require(rows.size < MAX_CONTACT_IMPORT_ROWS + 1) { "The selected CSV file has too many rows." }
        rows += row
    }
}

private fun parseXlsx(input: InputStream): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    var remainingBytes = MAX_FILE_TRANSFER_BYTES
    var entryCount = 0
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            require(entryCount <= MAX_WORKBOOK_ENTRIES) { "The selected workbook has too many parts." }
            if (!entry.isDirectory && entry.name in setOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml")) {
                require(!entries.containsKey(entry.name)) { MALFORMED_XLSX }
                val bytes = zip.readBoundedFileBytes("Workbook", remainingBytes)
                remainingBytes -= bytes.size
                entries[entry.name] = bytes
            }
            zip.closeEntry()
        }
    }
    val sheet = entries["xl/worksheets/sheet1.xml"]
        ?: throw IllegalArgumentException("The workbook does not contain a readable first worksheet.")
    val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
    val rows = mutableListOf<List<String>>()
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(sheet.inputStream(), "UTF-8")
    }
    var event = parser.eventType
    var currentRow: MutableMap<Int, String>? = null
    var nextColumn = 0
    var column = 0
    var cellType = ""
    var cellValue = ""
    var inValue = false
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> { currentRow = mutableMapOf(); nextColumn = 0 }
                "c" -> {
                    val reference = parser.getAttributeValue(null, "r").orEmpty()
                    column = if (reference.isBlank()) nextColumn else columnIndex(reference)
                    require(column < MAX_CONTACT_IMPORT_COLUMNS) { MALFORMED_XLSX }
                    nextColumn = column + 1
                    cellType = parser.getAttributeValue(null, "t").orEmpty()
                    cellValue = ""
                    inValue = false
                }
                "v" -> inValue = true
                "t" -> if (cellType == "inlineStr") inValue = true
            }
            XmlPullParser.TEXT -> if (inValue) {
                cellValue += parser.text
                require(cellValue.length <= MAX_CONTACT_FIELD_CHARS) { "A contact field is too long." }
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "v", "t" -> inValue = false
                "c" -> {
                    val value = if (cellType == "s") {
                        val index = cellValue.toIntOrNull() ?: throw IllegalArgumentException(MALFORMED_XLSX)
                        shared.getOrNull(index) ?: throw IllegalArgumentException(MALFORMED_XLSX)
                    } else cellValue
                    currentRow?.set(column, value)
                }
                "row" -> {
                    val values = currentRow.orEmpty()
                    require(values.size <= MAX_CONTACT_IMPORT_COLUMNS) { MALFORMED_XLSX }
                    require(rows.size < MAX_CONTACT_IMPORT_ROWS + 1) { "The selected workbook has too many rows." }
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
            XmlPullParser.TEXT -> if (inString && inText) {
                text.append(parser.text)
                require(text.length <= MAX_CONTACT_FIELD_CHARS) { "A contact field is too long." }
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inText = false
                "si" -> {
                    require(result.size < MAX_WORKBOOK_SHARED_STRINGS) { MALFORMED_XLSX }
                    result += text.toString()
                    inString = false
                }
            }
        }
        event = parser.next()
    }
    return result
}

private fun columnIndex(reference: String): Int {
    val letters = reference.takeWhile(Char::isLetter).uppercase()
    require(letters.isNotBlank()) { MALFORMED_XLSX }
    var index = 0
    letters.forEach { index = index * 26 + (it - 'A' + 1) }
    return index - 1
}
