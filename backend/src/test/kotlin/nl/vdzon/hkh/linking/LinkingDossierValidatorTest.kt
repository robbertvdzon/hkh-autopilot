package nl.vdzon.hkh.linking

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.ALLOWED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.CERTAIN
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.CONFIRMED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.HYPOTHESIS
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.NOT_ALLOWED
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.PUBLIC
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.UNCLEAR
import nl.vdzon.hkh.linking.LinkingDossierControlledValues.UNKNOWN
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class LinkingDossierValidatorTest {
    private val validator = LinkingDossierValidator()

    @Test
    fun `publishes a complete confirmed dossier including object media`() {
        val result = validator.validate(validDossier())

        assertEquals(DossierStatus.PUBLISHABLE_AS_METADATA_LINK, result.status)
        assertEquals("publiceerbaar als metadata-link", result.status.value)
        assertTrue(result.objectMediaAllowed)
        assertEquals(emptyList(), result.metadataLinkBlockingFieldPaths)
        assertEquals(emptyList(), result.objectMediaBlockingFieldPaths)
    }

    @TestFactory
    fun `reports every missing required record field`(): List<DynamicTest> = listOf(
        RecordCase(
            name = "source holder",
            change = { copy(sourceHolder = "  ") },
            expectedMetadataPaths = listOf("records[0].sourceHolder"),
        ),
        RecordCase(
            name = "stable reference alternatives",
            change = { copy(permanentUrl = null, identifier = " ") },
            expectedMetadataPaths = listOf("records[0].identifier", "records[0].permanentUrl"),
        ),
        RecordCase(
            name = "description alternatives",
            change = { copy(title = null, description = " ") },
            expectedMetadataPaths = listOf("records[0].description", "records[0].title"),
        ),
        RecordCase(
            name = "dating object",
            change = { copy(dating = null) },
            expectedMetadataPaths = listOf("records[0].dating.uncertainty", "records[0].dating.value"),
        ),
        RecordCase(
            name = "dating value",
            change = { copy(dating = dating?.copy(value = "  ")) },
            expectedMetadataPaths = listOf("records[0].dating.value"),
        ),
        RecordCase(
            name = "dating uncertainty",
            change = { copy(dating = dating?.copy(uncertainty = null)) },
            expectedMetadataPaths = listOf("records[0].dating.uncertainty"),
        ),
        RecordCase(
            name = "metadata rights",
            change = { copy(metadataRights = null) },
            expectedMetadataPaths = listOf("records[0].metadataRights"),
        ),
        RecordCase(
            name = "object rights",
            change = { copy(objectRights = " ") },
            expectedMetadataPaths = listOf("records[0].objectRights"),
            expectedObjectMediaPaths = listOf("records[0].objectRights"),
        ),
        RecordCase(
            name = "privacy classification",
            change = { copy(privacyClassification = null) },
            expectedMetadataPaths = listOf("records[0].privacyClassification"),
        ),
    ).map { case ->
        DynamicTest.dynamicTest(case.name) {
            val dossier = validDossier().replaceRecord(0, case.change)

            val result = validator.validate(dossier)

            assertEquals(DossierStatus.BLOCKED, result.status)
            assertEquals(case.expectedMetadataPaths, result.metadataLinkBlockingFieldPaths)
            assertEquals(case.expectedObjectMediaPaths, result.objectMediaBlockingFieldPaths)
            assertEquals(case.expectedObjectMediaPaths.isEmpty(), result.objectMediaAllowed)
        }
    }

    @TestFactory
    fun `reports every missing required relation field`(): List<DynamicTest> = listOf(
        RelationCase("relation type", { copy(relationType = " ") }, "relation.relationType"),
        RelationCase("connection basis", { copy(connectionBasis = null) }, "relation.connectionBasis"),
        RelationCase("evidence links", { copy(evidenceLinks = emptyList()) }, "relation.evidenceLinks"),
        RelationCase("confirmation status", { copy(confirmationStatus = null) }, "relation.confirmationStatus"),
    ).map { case ->
        DynamicTest.dynamicTest(case.name) {
            val dossier = validDossier().let { it.copy(relation = case.change(checkNotNull(it.relation))) }

            val result = validator.validate(dossier)

            assertEquals(DossierStatus.BLOCKED, result.status)
            assertEquals(listOf(case.expectedPath), result.metadataLinkBlockingFieldPaths)
            assertTrue(result.objectMediaAllowed)
        }
    }

    @Test
    fun `blocks a hypothesis as unconfirmed`() {
        val dossier = validDossier().let {
            it.copy(relation = checkNotNull(it.relation).copy(confirmationStatus = HYPOTHESIS))
        }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.BLOCKED, result.status)
        assertEquals(listOf("relation.confirmationStatus"), result.metadataLinkBlockingFieldPaths)
    }

    @TestFactory
    fun `keeps metadata publishable for recognized restrictive object rights`(): List<DynamicTest> =
        listOf(NOT_ALLOWED, UNCLEAR).map { objectRights ->
            DynamicTest.dynamicTest(objectRights) {
                val dossier = validDossier().replaceRecord(1) { copy(objectRights = objectRights) }

                val result = validator.validate(dossier)

                assertEquals(DossierStatus.PUBLISHABLE_AS_METADATA_LINK, result.status)
                assertFalse(result.objectMediaAllowed)
                assertEquals(emptyList(), result.metadataLinkBlockingFieldPaths)
                assertEquals(listOf("records[1].objectRights"), result.objectMediaBlockingFieldPaths)
            }
        }

    @Test
    fun `unknown controlled values fail closed`() {
        val dossier = validDossier()
            .replaceRecord(0) {
                copy(
                    dating = dating?.copy(uncertainty = UNKNOWN),
                    metadataRights = "vrij te gebruiken",
                    objectRights = "vrij te gebruiken",
                    privacyClassification = "intern",
                )
            }
            .let { it.copy(relation = checkNotNull(it.relation).copy(confirmationStatus = "waarschijnlijk")) }

        val result = validator.validate(dossier)

        assertEquals(
            listOf(
                "records[0].dating.uncertainty",
                "records[0].metadataRights",
                "records[0].objectRights",
                "records[0].privacyClassification",
                "relation.confirmationStatus",
            ),
            result.metadataLinkBlockingFieldPaths,
        )
        assertEquals(listOf("records[0].objectRights"), result.objectMediaBlockingFieldPaths)
    }

    @Test
    fun `blocks a dossier with another record count for both decisions`() {
        val dossier = validDossier().let { it.copy(records = it.records.take(1)) }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.BLOCKED, result.status)
        assertFalse(result.objectMediaAllowed)
        assertEquals(listOf("records"), result.metadataLinkBlockingFieldPaths)
        assertEquals(listOf("records"), result.objectMediaBlockingFieldPaths)
    }

    @Test
    fun `rejects every malformed supplied URL even when alternatives are valid`() {
        val dossier = validDossier()
            .replaceRecord(0) { copy(permanentUrl = "ftp://example.com/record-1", identifier = "record-1") }
            .let {
                it.copy(
                    relation = checkNotNull(it.relation).copy(
                        evidenceLinks = listOf("https://evidence.example/proof", "relative/proof"),
                    ),
                )
            }

        val result = validator.validate(dossier)

        assertEquals(
            listOf("records[0].permanentUrl", "relation.evidenceLinks[1]"),
            result.metadataLinkBlockingFieldPaths,
        )
    }

    @Test
    fun `reports both fields when records share a stable reference across alternatives`() {
        val dossier = validDossier().replaceRecord(1) {
            copy(identifier = "https://records.example/1")
        }

        val result = validator.validate(dossier)

        assertEquals(DossierStatus.BLOCKED, result.status)
        assertEquals(
            listOf("records[0].permanentUrl", "records[1].identifier"),
            result.metadataLinkBlockingFieldPaths,
        )
    }

    @Test
    fun `returns every blocker once in deterministic lexicographic order`() {
        val dossier = validDossier()
            .let { it.copy(records = it.records.take(1)) }
            .replaceRecord(0) {
                copy(
                    sourceHolder = " ",
                    title = null,
                    description = null,
                    metadataRights = UNCLEAR,
                    objectRights = UNCLEAR,
                )
            }
            .let {
                it.copy(
                    relation = LinkingRelation(
                        relationType = null,
                        connectionBasis = " ",
                        evidenceLinks = emptyList(),
                        confirmationStatus = HYPOTHESIS,
                    ),
                )
            }

        val result = validator.validate(dossier)

        assertEquals(
            listOf(
                "records",
                "records[0].description",
                "records[0].metadataRights",
                "records[0].sourceHolder",
                "records[0].title",
                "relation.confirmationStatus",
                "relation.connectionBasis",
                "relation.evidenceLinks",
                "relation.relationType",
            ),
            result.metadataLinkBlockingFieldPaths,
        )
        assertEquals(listOf("records", "records[0].objectRights"), result.objectMediaBlockingFieldPaths)
        assertEquals(result.metadataLinkBlockingFieldPaths.distinct(), result.metadataLinkBlockingFieldPaths)
        assertEquals(result.metadataLinkBlockingFieldPaths.sorted(), result.metadataLinkBlockingFieldPaths)
    }

    @Test
    fun `reports all relation fields when the relation is absent`() {
        val result = validator.validate(validDossier().copy(relation = null))

        assertEquals(
            listOf(
                "relation.confirmationStatus",
                "relation.connectionBasis",
                "relation.evidenceLinks",
                "relation.relationType",
            ),
            result.metadataLinkBlockingFieldPaths,
        )
    }

    private fun validDossier() = LinkingDossier(
        records = listOf(
            validRecord("1", "Collectie HKH"),
            validRecord("2", "Gemeentearchief"),
        ),
        relation = LinkingRelation(
            relationType = "beschrijft hetzelfde object",
            connectionBasis = "Inventarisnummers en datering komen overeen",
            evidenceLinks = listOf("https://evidence.example/linking-analysis"),
            confirmationStatus = CONFIRMED,
        ),
    )

    private fun validRecord(id: String, sourceHolder: String) = LinkingRecord(
        sourceHolder = sourceHolder,
        permanentUrl = "https://records.example/$id",
        identifier = "record-$id",
        title = "Record $id",
        description = null,
        dating = RecordDating("1920", CERTAIN),
        metadataRights = ALLOWED,
        objectRights = ALLOWED,
        privacyClassification = PUBLIC,
    )

    private fun LinkingDossier.replaceRecord(
        index: Int,
        change: LinkingRecord.() -> LinkingRecord,
    ): LinkingDossier = copy(records = records.mapIndexed { currentIndex, record ->
        if (currentIndex == index) record.change() else record
    })

    private data class RecordCase(
        val name: String,
        val change: LinkingRecord.() -> LinkingRecord,
        val expectedMetadataPaths: List<String>,
        val expectedObjectMediaPaths: List<String> = emptyList(),
    )

    private data class RelationCase(
        val name: String,
        val change: LinkingRelation.() -> LinkingRelation,
        val expectedPath: String,
    )
}
