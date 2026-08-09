package nl.vdzon.hkh.news.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import nl.vdzon.hkh.auth.AdminAuthenticator
import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import nl.vdzon.hkh.news.AggregatedNewsEntity
import nl.vdzon.hkh.news.LatestNews
import nl.vdzon.hkh.news.LatestNewsService
import nl.vdzon.hkh.news.NewsSearchItem
import nl.vdzon.hkh.news.entity.MatchedNewsEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class LatestNewsResponse(
    val id: Long,
    val title: String,
    val message: String,
    val publishedAt: Instant,
    val createdAt: Instant,
)

data class NewsEntityResponse(val type: String, val label: String)

data class AggregatedNewsEntityResponse(val type: String, val label: String, val itemCount: Int)

data class LatestNewsItemResponse(
    val id: Long,
    val title: String,
    val message: String,
    val publishedAt: Instant,
    val createdAt: Instant,
    val entities: List<NewsEntityResponse>,
    val source: String,
)

data class LatestNewsListResponse(
    val items: List<LatestNewsItemResponse>,
    val total: Int,
    val entities: List<AggregatedNewsEntityResponse>,
)

data class CreateLatestNewsRequest(
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:NotBlank @field:Size(max = 10_000) val message: String,
)

@RestController
@RequestMapping("/api/news")
class LatestNewsController(private val service: LatestNewsService) {
    @GetMapping
    fun findAll(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) entity: String?,
    ): LatestNewsListResponse {
        val result = service.search(q, entity)
        return LatestNewsListResponse(
            items = result.items.map(NewsSearchItem::toResponse),
            total = result.total,
            entities = result.entities.map(AggregatedNewsEntity::toResponse),
        )
    }
}

@RestController
@RequestMapping("/api/admin/news")
class AdminLatestNewsController(
    private val service: LatestNewsService,
    private val authenticator: AdminAuthenticator,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateLatestNewsRequest,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestHeader(PreviewRuntimeConfig.ADMIN_HEADER, required = false) previewHeader: String?,
    ): LatestNewsResponse {
        val admin = authenticator.authenticate(authorization, previewHeader)
        return service.create(request.title, request.message, admin.email).toResponse()
    }
}

private fun LatestNews.toResponse() = LatestNewsResponse(id, title, message, publishedAt, createdAt)

private fun MatchedNewsEntity.toResponse() = NewsEntityResponse(type = type.name, label = canonicalLabel)

private fun AggregatedNewsEntity.toResponse() =
    AggregatedNewsEntityResponse(type = type.name, label = canonicalLabel, itemCount = itemCount)

private fun NewsSearchItem.toResponse() = LatestNewsItemResponse(
    id = news.id,
    title = news.title,
    message = news.message,
    publishedAt = news.publishedAt,
    createdAt = news.createdAt,
    entities = entities.map(MatchedNewsEntity::toResponse),
    source = "Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op ${news.publishedAt}",
)
