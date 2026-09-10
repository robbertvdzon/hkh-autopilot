package nl.vdzon.hkh.topicsearch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopicSearchRecordMapperTest {

    @Test
    fun `a publicdomain mark rights url maps to publiek domein`() {
        val badge = deriveTopicSearchLicenseBadge("http://creativecommons.org/publicdomain/mark/1.0/")

        assertEquals("Publiek domein", badge.text)
    }

    @Test
    fun `a creativecommons licenses url derives the literal variant from the path segment`() {
        assertEquals("CC BY-SA", deriveTopicSearchLicenseBadge("http://creativecommons.org/licenses/by-sa/4.0/").text)
        assertEquals("CC BY", deriveTopicSearchLicenseBadge("http://creativecommons.org/licenses/by/4.0/").text)
        assertEquals("CC BY-NC-ND", deriveTopicSearchLicenseBadge("http://creativecommons.org/licenses/by-nc-nd/4.0/").text)
    }

    @Test
    fun `an InC rightsstatements url maps to rechten voorbehouden`() {
        val badge = deriveTopicSearchLicenseBadge("http://rightsstatements.org/vocab/InC/1.0/")

        assertEquals("Rechten voorbehouden", badge.text)
    }

    @Test
    fun `an unrecognized rights url keeps the raw url as link with an unknown badge`() {
        val badge = deriveTopicSearchLicenseBadge("https://example.test/unknown-rights")

        assertEquals("Rechten onbekend", badge.text)
        assertEquals("https://example.test/unknown-rights", badge.url)
    }

    @Test
    fun `a missing rights url yields an unknown badge without a link`() {
        val badge = deriveTopicSearchLicenseBadge(null)

        assertEquals("Rechten onbekend", badge.text)
        assertNull(badge.url)
    }

    @Test
    fun `a record with a title, dataProvider and edmIsShownAt is valid`() {
        val record = buildTopicSearchRecordOrNull(
            titles = listOf("Watersnood van 1916"),
            descriptions = null,
            dataProviders = listOf("Noord-Hollands Archief"),
            edmIsShownAt = listOf("https://archief.example/record/1"),
            guid = "https://www.europeana.eu/item/1",
            rights = listOf("http://creativecommons.org/publicdomain/mark/1.0/"),
        )

        assertEquals("Watersnood van 1916", record?.title)
        assertEquals("Noord-Hollands Archief", record?.dataProvider)
        assertEquals("https://archief.example/record/1", record?.sourceUrl)
    }

    @Test
    fun `a missing title falls back to the description`() {
        val record = buildTopicSearchRecordOrNull(
            titles = null,
            descriptions = listOf("Foto van de watersnood"),
            dataProviders = listOf("Noord-Hollands Archief"),
            edmIsShownAt = null,
            guid = "https://www.europeana.eu/item/1",
            rights = null,
        )

        assertEquals("Foto van de watersnood", record?.title)
        assertEquals("https://www.europeana.eu/item/1", record?.sourceUrl)
    }

    @Test
    fun `missing both title and description is invalid`() {
        val record = buildTopicSearchRecordOrNull(
            titles = null,
            descriptions = null,
            dataProviders = listOf("Noord-Hollands Archief"),
            edmIsShownAt = listOf("https://archief.example/record/1"),
            guid = null,
            rights = null,
        )

        assertNull(record)
    }

    @Test
    fun `missing dataProvider is invalid`() {
        val record = buildTopicSearchRecordOrNull(
            titles = listOf("Watersnood van 1916"),
            descriptions = null,
            dataProviders = null,
            edmIsShownAt = listOf("https://archief.example/record/1"),
            guid = null,
            rights = null,
        )

        assertNull(record)
    }

    @Test
    fun `missing both edmIsShownAt and guid is invalid`() {
        val record = buildTopicSearchRecordOrNull(
            titles = listOf("Watersnood van 1916"),
            descriptions = null,
            dataProviders = listOf("Noord-Hollands Archief"),
            edmIsShownAt = null,
            guid = null,
            rights = null,
        )

        assertNull(record)
    }
}
