package com.faheem.habits.features.contacts


import com.faheem.habits.core.data.sync.MAX_FILE_TRANSFER_BYTES
import com.faheem.habits.core.data.sync.requireWithinFileLimit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContactImportTest {
    @Test
    fun parsesVCardWithMultipleContactsAndPhoneNumbers() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            N:Doe;Jane;;;
            TEL;TYPE=CELL:+1 (555) 010-1234
            TEL;TYPE=WORK:+1-555-010-5678
            EMAIL:jane@example.com
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:John Smith
            TEL:555-010-9876
            END:VCARD
        """.trimIndent()

        val contacts = parseContacts(ByteArrayInputStream(vcard.toByteArray()), "address-book.vcf")

        assertEquals(2, contacts.size)
        assertEquals("Jane Doe", contacts[0].name)
        assertEquals(listOf("+1 (555) 010-1234", "+1-555-010-5678"), contacts[0].phoneNumbers)
        assertEquals("jane@example.com", contacts[0].email)
        assertEquals("John Smith", contacts[1].name)
        assertEquals(listOf("555-010-9876"), contacts[1].phoneNumbers)
    }

    @Test
    fun unfoldsVCardLinesAndDecodesEscapedNotes() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Jane Doe
            NOTE:Met at the conference\, follow up
             next week
            END:VCARD
        """.trimIndent()

        val contact = parseContacts(ByteArrayInputStream(vcard.toByteArray()), "contact.vcf").single()

        assertEquals("Met at the conference, follow upnext week", contact.notes)
    }

    @Test
    fun rejectsOversizedVCard() {
        val oversized = ByteArray(MAX_FILE_TRANSFER_BYTES + 1) { 'x'.code.toByte() }

        val error = runCatching { parseContacts(ByteArrayInputStream(oversized), "oversized.vcf") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("10 MB"))
    }

    @Test
    fun rejectsOversizedXlsxWorksheet() {
        val workbook = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(ByteArray(MAX_FILE_TRANSFER_BYTES + 1))
                zip.closeEntry()
            }
            output.toByteArray()
        }

        val error = runCatching { parseContacts(ByteArrayInputStream(workbook), "oversized.xlsx") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("10 MB"))
    }

    @Test
    fun rejectsOversizedDownloadedBytes() {
        val error = runCatching { ByteArray(MAX_FILE_TRANSFER_BYTES + 1).requireWithinFileLimit("Downloaded file") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("10 MB"))
    }

    @Test
    fun rejectsMalformedVCardWithFixedMessage() {
        val malformed = "BEGIN:VCARD\nFN:Jane Doe\n"

        val error = runCatching {
            parseContacts(ByteArrayInputStream(malformed.toByteArray()), "broken.vcf")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("The selected VCF file is malformed.", error!!.message)
    }

    @Test
    fun rejectsUnterminatedCsvQuote() {
        val malformed = "Name,Phone\n\"Jane,555"

        val error = runCatching {
            parseContacts(ByteArrayInputStream(malformed.toByteArray()), "broken.csv")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("The selected CSV file has an unclosed quoted field.", error!!.message)
    }

    @Test
    fun rejectsCsvWithTooManyRows() {
        val rows = buildString {
            appendLine("Name,Phone")
            repeat(MAX_CONTACT_IMPORT_ROWS + 2) { appendLine("Person$it,555-010-$it") }
        }

        val error = runCatching {
            parseContacts(ByteArrayInputStream(rows.toByteArray()), "huge.csv")
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("too many rows"))
    }

    @Test
    fun rejectsCsvWithTooManyColumns() {
        val header = (listOf("Name") + List(32) { "Extra$it" }).joinToString(",")

        val error = runCatching {
            parseContacts(ByteArrayInputStream("$header\nJane,1".toByteArray()), "wide.csv")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("The selected CSV file is malformed.", error!!.message)
    }

    @Test
    fun rejectsOversizedCsvField() {
        val csv = "Name,Phone\n${"x".repeat(MAX_CONTACT_FIELD_CHARS + 1)},555"

        val error = runCatching {
            parseContacts(ByteArrayInputStream(csv.toByteArray()), "field.csv")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("A contact field is too long.", error!!.message)
    }

    @Test
    fun rejectsWorkbookWithoutFirstWorksheet() {
        val workbook = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
                zip.write("<worksheet/>".toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        val error = runCatching {
            parseContacts(ByteArrayInputStream(workbook), "missing-sheet.xlsx")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("The workbook does not contain a readable first worksheet.", error!!.message)
    }

    @Test
    fun rejectsWorkbookWithTooManyParts() {
        val workbook = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                repeat(201) { index ->
                    zip.putNextEntry(ZipEntry("doc/part-$index"))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

        val error = runCatching {
            parseContacts(ByteArrayInputStream(workbook), "many-parts.xlsx")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals("The selected workbook has too many parts.", error!!.message)
    }

    @Test
    fun rejectsLegacyXlsWithUpgradeMessage() {
        val error = runCatching {
            parseContacts(ByteArrayInputStream(byteArrayOf()), "old.xls")
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(
            "Legacy .xls files are not supported. Save the workbook as .xlsx or CSV.",
            error!!.message
        )
    }
}
