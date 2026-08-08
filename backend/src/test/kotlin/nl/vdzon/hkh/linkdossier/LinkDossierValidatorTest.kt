package nl.vdzon.hkh.linkdossier

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class LinkDossierValidatorTest {

    private val validator = LinkDossierValidator()

    @Test
    fun `complete dossier with confirmed relation is publishable and allows object media`() {
        val result = validator.validate(validDossier())

        assertEquals(DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK, result.status)
        assertTrue(result.objectMediaAllowed)
        assertEquals(emptyList(), result.metadataBlockingFieldPaths)
        assertEquals(emptyList(), result.objectMediaBlockingFieldPaths)
    }

    @ParameterizedTest(name = "missing {0} blocks the metadata link")
    @MethodSource("missingRecordFieldScenarios")
    fun `every mandatory record field blocks the metadata link when missing`(
        expectedPath: String,
        mutate: (LinkDossierRecord) -> LinkDossierRecord,
    ) {
        val dossier = validDossier().let { it.copy(records = listOf(mutate(it.records[0]), it.records[1])) }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf(expectedPath), result.metadataBlockingFieldPaths)
        assertTrue(result.objectMediaAllowed)
    }

    @Test
    fun `blank alternative pair reports both permanent url and identifier`() {
        val result = validator.validate(
            withFirstRecord { it.copy(permanentUrl = "  ", identifier = null) },
        )

        assertEquals(
            listOf("records[0].identifier", "records[0].permanentUrl"),
            result.metadataBlockingFieldPaths,
        )
    }

    @Test
    fun `blank alternative pair reports both title and description`() {
        val result = validator.validate(
            withFirstRecord { it.copy(title = "", description = "   ") },
        )

        assertEquals(
            listOf("records[0].description", "records[0].title"),
            result.metadataBlockingFieldPaths,
        )
    }

    @Test
    fun `one filled alternative is enough`() {
        val result = validator.validate(
            withFirstRecord { it.copy(permanentUrl = null, title = null) },
        )

        assertEquals(DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK, result.status)
        assertEquals(emptyList(), result.metadataBlockingFieldPaths)
    }

    @Test
    fun `hypothesis relation always blocks the metadata link`() {
        val dossier = validDossier().let { it.copy(relation = it.relation.copy(confirmationStatus = "hypothese")) }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf("relation.confirmationStatus"), result.metadataBlockingFieldPaths)
        assertTrue(result.objectMediaAllowed)
    }

    @ParameterizedTest(name = "object rights `{0}` block object media only")
    @ValueSource(strings = ["", "niet toegestaan", "onduidelijk", "iets anders"])
    fun `missing not allowed and unclear object rights block object media only`(objectRights: String) {
        val result = validator.validate(withFirstRecord { it.copy(objectRights = objectRights) })

        assertEquals(DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(listOf("records[0].objectRights"), result.objectMediaBlockingFieldPaths)
        assertEquals(emptyList(), result.metadataBlockingFieldPaths)
    }

    @Test
    fun `absent object rights block object media only`() {
        val result = validator.validate(withFirstRecord { it.copy(objectRights = null) })

        assertEquals(DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(listOf("records[0].objectRights"), result.objectMediaBlockingFieldPaths)
    }

    @Test
    fun `dossier blocked for other reasons may still allow object media`() {
        val dossier = validDossier().let { it.copy(relation = it.relation.copy(confirmationStatus = "hypothese")) }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertTrue(result.objectMediaAllowed)
        assertEquals(emptyList(), result.objectMediaBlockingFieldPaths)
    }

    @ParameterizedTest(name = "record count {0} blocks both lists")
    @ValueSource(ints = [0, 1, 3])
    fun `a record count other than two blocks the dossier and object media`(count: Int) {
        val template = validDossier().records[0]
        val records = (0 until count).map { index ->
            template.copy(permanentUrl = "https://example.org/record/$index")
        }

        val result = validator.validate(validDossier().copy(records = records))

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(listOf("records"), result.metadataBlockingFieldPaths)
        assertEquals(listOf("records"), result.objectMediaBlockingFieldPaths)
    }

    @ParameterizedTest(name = "permanent url `{0}` is rejected")
    @ValueSource(strings = ["not a url", "ftp://example.org/a", "/relative/path", "http://", "ht tp://x"])
    fun `an invalid permanent url blocks its own path even with a valid identifier`(url: String) {
        val result = validator.validate(
            withFirstRecord { it.copy(permanentUrl = url, identifier = "HKH-0001") },
        )

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf("records[0].permanentUrl"), result.metadataBlockingFieldPaths)
    }

    @ParameterizedTest(name = "evidence links {0} are rejected once")
    @MethodSource("invalidEvidenceLinkScenarios")
    fun `invalid or absent evidence links block the evidence path exactly once`(links: List<String?>) {
        val dossier = validDossier().let { it.copy(relation = it.relation.copy(evidenceLinks = links)) }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf("relation.evidenceLinks"), result.metadataBlockingFieldPaths)
    }

    @Test
    fun `two records with the same stable url reference are blocked`() {
        val dossier = validDossier()
        val shared = " https://example.org/record/shared "

        val result = validator.validate(
            dossier.copy(
                records = listOf(
                    dossier.records[0].copy(permanentUrl = shared.trim()),
                    dossier.records[1].copy(permanentUrl = shared),
                ),
            ),
        )

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(
            listOf("records[0].permanentUrl", "records[1].permanentUrl"),
            result.metadataBlockingFieldPaths,
        )
    }

    @Test
    fun `two records falling back on the same identifier are blocked on the identifier paths`() {
        val dossier = validDossier()

        val result = validator.validate(
            dossier.copy(
                records = listOf(
                    dossier.records[0].copy(permanentUrl = null, identifier = "HKH-42"),
                    dossier.records[1].copy(permanentUrl = null, identifier = " HKH-42 "),
                ),
            ),
        )

        assertEquals(
            listOf("records[0].identifier", "records[1].identifier"),
            result.metadataBlockingFieldPaths,
        )
    }

    @Test
    fun `records without any usable reference skip the distinctness comparison`() {
        val dossier = validDossier()

        val result = validator.validate(
            dossier.copy(
                records = dossier.records.map { it.copy(permanentUrl = null, identifier = null) },
            ),
        )

        assertEquals(
            listOf(
                "records[0].identifier",
                "records[0].permanentUrl",
                "records[1].identifier",
                "records[1].permanentUrl",
            ),
            result.metadataBlockingFieldPaths,
        )
    }

    @ParameterizedTest(name = "unrecognized controlled value on {0}")
    @MethodSource("unrecognizedControlledValueScenarios")
    fun `unrecognized controlled values block their own path`(
        expectedPath: String,
        mutate: (LinkDossier) -> LinkDossier,
    ) {
        val result = validator.validate(mutate(validDossier()))

        val reportedIn = if (expectedPath.endsWith(LinkDossierFieldPaths.OBJECT_RIGHTS)) {
            result.objectMediaBlockingFieldPaths
        } else {
            result.metadataBlockingFieldPaths
        }
        assertEquals(listOf(expectedPath), reportedIn)
    }

    @Test
    fun `restricted privacy classification blocks the metadata link`() {
        val result = validator.validate(withFirstRecord { it.copy(privacyClassification = "beperkt") })

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf("records[0].privacyClassification"), result.metadataBlockingFieldPaths)
    }

    @Test
    fun `metadata rights that are not explicitly allowed block the metadata link`() {
        val result = validator.validate(withFirstRecord { it.copy(metadataRights = "onduidelijk") })

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertEquals(listOf("records[0].metadataRights"), result.metadataBlockingFieldPaths)
    }

    @Test
    fun `an explicitly unknown dating uncertainty is accepted while an unrecognized one is not`() {
        val accepted = validator.validate(
            withFirstRecord { it.copy(dating = RecordDating(value = "circa 1650", uncertainty = "onbekend")) },
        )
        val rejected = validator.validate(
            withFirstRecord { it.copy(dating = RecordDating(value = "circa 1650", uncertainty = "misschien")) },
        )

        assertEquals(DossierStatus.PUBLICEERBAAR_ALS_METADATA_LINK, accepted.status)
        assertEquals(listOf("records[0].dating.uncertainty"), rejected.metadataBlockingFieldPaths)
    }

    @Test
    fun `multiple simultaneous failures are deduplicated and lexicographically ordered`() {
        val dossier = LinkDossier(
            records = listOf(
                LinkDossierRecord(
                    sourceHolder = "   ",
                    permanentUrl = "not a url",
                    identifier = "HKH-1",
                    title = null,
                    description = " ",
                    dating = RecordDating(value = null, uncertainty = "vaag"),
                    metadataRights = "onduidelijk",
                    objectRights = null,
                    privacyClassification = "beperkt",
                ),
                LinkDossierRecord(
                    sourceHolder = "Regionaal Archief",
                    permanentUrl = null,
                    identifier = "HKH-1",
                    title = "Tweede record",
                    dating = RecordDating(value = "1651", uncertainty = "zeker"),
                    metadataRights = "toegestaan",
                    objectRights = "niet toegestaan",
                    privacyClassification = "openbaar",
                ),
            ),
            relation = LinkDossierRelation(
                relationType = " ",
                connectionGround = null,
                evidenceLinks = listOf("ftp://example.org/bewijs"),
                confirmationStatus = "hypothese",
            ),
        )

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(
            listOf(
                "records[0].dating.uncertainty",
                "records[0].dating.value",
                "records[0].description",
                "records[0].metadataRights",
                "records[0].permanentUrl",
                "records[0].privacyClassification",
                "records[0].sourceHolder",
                "records[0].title",
                "relation.confirmationStatus",
                "relation.connectionGround",
                "relation.evidenceLinks",
                "relation.relationType",
            ),
            result.metadataBlockingFieldPaths,
        )
        assertEquals(
            listOf("records[0].objectRights", "records[1].objectRights"),
            result.objectMediaBlockingFieldPaths,
        )
        assertEquals(
            result.metadataBlockingFieldPaths.sorted(),
            result.metadataBlockingFieldPaths,
        )
        assertEquals(
            result.metadataBlockingFieldPaths.distinct().size,
            result.metadataBlockingFieldPaths.size,
        )
    }

    @Test
    fun `an empty dossier never throws and stays blocked`() {
        val result = validator.validate(LinkDossier())

        assertEquals(DossierStatus.GEBLOKKEERD, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(listOf("records"), result.objectMediaBlockingFieldPaths)
        assertTrue("records" in result.metadataBlockingFieldPaths)
    }

    private fun withFirstRecord(mutate: (LinkDossierRecord) -> LinkDossierRecord): LinkDossier =
        validDossier().let { it.copy(records = listOf(mutate(it.records[0]), it.records[1])) }

    companion object {
        private fun validRecord(index: Int) = LinkDossierRecord(
            sourceHolder = "Historische Kring Heemskerk",
            permanentUrl = "https://example.org/record/$index",
            identifier = "HKH-$index",
            title = "Record $index",
            description = "Beschrijving van record $index",
            dating = RecordDating(value = "1650", uncertainty = "geschat"),
            metadataRights = "toegestaan",
            objectRights = "toegestaan",
            privacyClassification = "openbaar",
        )

        private fun validDossier() = LinkDossier(
            records = listOf(validRecord(0), validRecord(1)),
            relation = LinkDossierRelation(
                relationType = "beschrijft hetzelfde object",
                connectionGround = "Identieke inventarisnummers in beide beschrijvingen",
                evidenceLinks = listOf("https://example.org/bewijs/1"),
                confirmationStatus = "bevestigd",
            ),
        )

        @JvmStatic
        fun missingRecordFieldScenarios(): List<Arguments> = listOf(
            Arguments.of("records[0].sourceHolder", mutator { it.copy(sourceHolder = "   ") }),
            Arguments.of("records[0].dating.value", mutator { it.copy(dating = it.dating.copy(value = null)) }),
            Arguments.of(
                "records[0].dating.uncertainty",
                mutator { it.copy(dating = it.dating.copy(uncertainty = " ")) },
            ),
            Arguments.of("records[0].metadataRights", mutator { it.copy(metadataRights = null) }),
            Arguments.of("records[0].privacyClassification", mutator { it.copy(privacyClassification = null) }),
        )

        @JvmStatic
        fun invalidEvidenceLinkScenarios(): List<Arguments> = listOf(
            Arguments.of(emptyList<String?>()),
            Arguments.of(listOf<String?>(null)),
            Arguments.of(listOf<String?>("   ")),
            Arguments.of(listOf<String?>("geen url")),
            Arguments.of(listOf<String?>("https://example.org/ok", "ftp://example.org/fout", "nog fouter")),
        )

        @JvmStatic
        fun unrecognizedControlledValueScenarios(): List<Arguments> = listOf(
            Arguments.of(
                "records[0].metadataRights",
                dossierMutator { it.replaceFirstRecord { r -> r.copy(metadataRights = "misschien") } },
            ),
            Arguments.of(
                "records[0].objectRights",
                dossierMutator { it.replaceFirstRecord { r -> r.copy(objectRights = "misschien") } },
            ),
            Arguments.of(
                "records[0].privacyClassification",
                dossierMutator { it.replaceFirstRecord { r -> r.copy(privacyClassification = "half openbaar") } },
            ),
            Arguments.of(
                "relation.confirmationStatus",
                dossierMutator { it.copy(relation = it.relation.copy(confirmationStatus = "wellicht")) },
            ),
        )

        private fun mutator(block: (LinkDossierRecord) -> LinkDossierRecord) = block

        private fun dossierMutator(block: (LinkDossier) -> LinkDossier) = block

        private fun LinkDossier.replaceFirstRecord(block: (LinkDossierRecord) -> LinkDossierRecord) =
            copy(records = listOf(block(records[0]), records[1]))
    }
}
