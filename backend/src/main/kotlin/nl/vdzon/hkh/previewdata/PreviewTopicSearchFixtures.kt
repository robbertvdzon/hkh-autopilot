package nl.vdzon.hkh.previewdata

import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** Synthetic HTTP upstream: exercises the real client, mapper, service and UI in PR previews. */
@RestController
@ConditionalOnProperty(name = ["HKH_TOPICSEARCH_PREVIEW_FIXTURES"], havingValue = "true")
class PreviewTopicSearchFixtures(private val preview: PreviewRuntimeConfig) {
    init {
        require(preview.enabled && preview.prNumber != null) { "Topic fixtures require a verified disposable PR preview" }
    }

    @GetMapping("/test-fixtures/europeana/record/v2/search.json")
    fun search(@RequestParam query: String): ResponseEntity<Any> {
        if (query.contains("test-storing", ignoreCase = true)) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        if (query.contains("test-timeout", ignoreCase = true)) Thread.sleep(2500)
        if (query.contains("test-ongeldige-json", ignoreCase = true)) return ResponseEntity.ok().body("invalid-json")
        val items = if (query.contains("test-leeg", ignoreCase = true)) emptyList() else listOf(mapOf(
            "title" to listOf("Testrecord: watersnood van 1916 in Heemskerk"),
            "dcDescription" to listOf("Synthetische previewdata voor de zoekinterface; geen historische bron."),
            "dataProvider" to listOf("Preview-testcollectie"),
            "edmIsShownAt" to listOf("https://example.invalid/europeana/preview-1"),
            "guid" to "https://example.invalid/europeana/preview-1",
            "rights" to listOf("https://creativecommons.org/publicdomain/zero/1.0/")
        ))
        return ResponseEntity.ok(mapOf("success" to true, "items" to items, "totalResults" to items.size))
    }

    @GetMapping("/test-fixtures/wikidata/w/api.php")
    fun wikidata(): Map<String, Any> = mapOf("search" to emptyList<Any>(), "success" to 1)
}
