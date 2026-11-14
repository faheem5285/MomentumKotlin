package com.faheem.habits.features.contacts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

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
}
