package nl.vdzon.hkh.recordintake.api

import nl.vdzon.hkh.externalverification.ArchiveFetchResult
import nl.vdzon.hkh.externalverification.ArchivesNlClient
import nl.vdzon.hkh.externalverification.archivesNlUri
import nl.vdzon.hkh.recordintake.RecordIntakeArchiveUrlPattern
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RecordIntakeExternalArchivePreviewRequest(val durableUrl: String? = null)

data class RecordIntakeExternalArchivePreviewResponse(
    val status: String,
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val license: String? = null,
    val sourceUri: String? = null,
)

/** Machineleesbare statuslabels voor het niet-blokkerende preview-paneel. */
object RecordIntakeExternalArchivePreviewStatus {
    const val VERIFIED = "GEVERIFIEERD"
    const val NO_MATCH = "GEEN_MATCH"
    const val UNREACHABLE = "NIET_BEREIKBAAR"
}

/**
 * Niet-persisterend previewendpoint voor de externe conceptkoppeling: toetst `durableUrl` tegen het
 * archieven.nl-patroon en bevraagt bij een match [ArchivesNlClient.fetch] zonder autorisatietoken.
 * Volgt de URL het patroon niet, dan wordt nooit een netwerkaanroep gedaan. Er wordt uitsluitend de
 * gestructureerde kernvelden teruggegeven; de ruwe externe respons wordt nergens bewaard.
 */
@RestController
@RequestMapping("/api/record-intake")
class RecordIntakeExternalArchivePreviewController(private val archivesNlClient: ArchivesNlClient) {

    @PostMapping("/external-archive-preview")
    fun preview(
        @RequestBody request: RecordIntakeExternalArchivePreviewRequest,
    ): ResponseEntity<RecordIntakeExternalArchivePreviewResponse> {
        val reference = RecordIntakeArchiveUrlPattern.parse(request.durableUrl)
            ?: return ResponseEntity.ok(unreachable())

        val fetchResult = runCatching { archivesNlClient.fetch(reference.adtid, reference.guid, null) }
            .getOrElse { ArchiveFetchResult.NotFound }

        val response = when (fetchResult) {
            is ArchiveFetchResult.Found -> RecordIntakeExternalArchivePreviewResponse(
                status = RecordIntakeExternalArchivePreviewStatus.VERIFIED,
                name = fetchResult.fields.name,
                birthDate = fetchResult.fields.birthDate,
                deathDate = fetchResult.fields.deathDate,
                license = fetchResult.fields.license,
                sourceUri = archivesNlUri(reference.adtid, reference.guid),
            )
            ArchiveFetchResult.NotFound -> RecordIntakeExternalArchivePreviewResponse(
                status = RecordIntakeExternalArchivePreviewStatus.NO_MATCH,
            )
            ArchiveFetchResult.AuthenticationRequired -> unreachable()
        }
        return ResponseEntity.ok(response)
    }

    private fun unreachable() = RecordIntakeExternalArchivePreviewResponse(
        status = RecordIntakeExternalArchivePreviewStatus.UNREACHABLE,
    )
}
