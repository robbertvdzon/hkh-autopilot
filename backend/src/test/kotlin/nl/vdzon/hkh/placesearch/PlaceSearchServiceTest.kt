package nl.vdzon.hkh.placesearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * End-to-end dekking van `PlaceSearchService` tegen embedded JDK [HttpServer]-fixtures voor zowel
 * Wikidata als Wikimedia Commons: 0/1/>1 matches, P131-doorverwijzing, P625-coördinatenfilter,
 * ontbrekende P373 (fallback naar P18), en de 2s-timeout-/foutpaden.
 */
class PlaceSearchServiceTest {

    private val servers = mutableListOf<HttpServer>()
    private val executors = mutableListOf<ExecutorService>()

    @AfterTest
    fun tearDown() {
        servers.forEach { it.stop(0) }
        executors.forEach { it.shutdownNow() }
    }

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC)

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        servers += server
        return server
    }

    private fun restClientFor(server: HttpServer, readTimeoutMillis: Int = 3000): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(1000)
            setReadTimeout(readTimeoutMillis)
        }
        return RestClient.builder()
            .baseUrl("http://localhost:${server.address.port}")
            .requestFactory(requestFactory)
            .build()
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun query(exchange: HttpExchange): Map<String, String> =
        (exchange.requestURI.rawQuery ?: "").split("&").filter { it.isNotBlank() }.associate {
            val (key, value) = it.split("=", limit = 2)
            java.net.URLDecoder.decode(key, "UTF-8") to java.net.URLDecoder.decode(value, "UTF-8")
        }

    private fun service(
        wikidataServer: HttpServer,
        commonsServer: HttpServer = wikidataServer,
        deadlineMillis: Long = PLACE_SEARCH_DEADLINE_MILLIS,
        readTimeoutMillis: Int = 3000,
    ): PlaceSearchService {
        val executor = Executors.newFixedThreadPool(2).also { executors += it }
        return PlaceSearchService(
            wikidataClient = PlaceSearchWikidataClient(restClientFor(wikidataServer, readTimeoutMillis)),
            commonsClient = PlaceSearchCommonsClient(restClientFor(commonsServer, readTimeoutMillis)),
            answerBuilder = PlaceSearchAnswerBuilder(),
            executor = executor,
            clock = fixedClock,
            deadlineMillis = deadlineMillis,
        )
    }

    private fun entityDataJson(
        qid: String,
        label: String = "Kasteel Assumburg",
        description: String? = "kasteel in Heemskerk",
        locatedIn: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        inceptionTime: String? = null,
        heritageQid: String? = null,
        architecturalStyleQid: String? = null,
        architectQid: String? = null,
        commonsCategory: String? = null,
        imageFileName: String? = null,
    ): String {
        val claims = mutableListOf<String>()
        locatedIn?.let { claims += """"P131": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "$it"}}}}]""" }
        if (latitude != null && longitude != null) {
            claims += """"P625": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"latitude": $latitude, "longitude": $longitude}}}}]"""
        }
        inceptionTime?.let { claims += """"P571": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "$it"}}}}]""" }
        heritageQid?.let { claims += """"P1435": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "$it"}}}}]""" }
        architecturalStyleQid?.let { claims += """"P149": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "$it"}}}}]""" }
        architectQid?.let { claims += """"P84": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "$it"}}}}]""" }
        commonsCategory?.let { claims += """"P373": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "$it"}}}]""" }
        imageFileName?.let { claims += """"P18": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "$it"}}}]""" }
        val descriptionsJson = if (description != null) """"descriptions": {"nl": {"value": "$description"}},""" else ""
        return """
            {"entities": {"$qid": {
                "labels": {"nl": {"value": "$label"}},
                $descriptionsJson
                "claims": {${claims.joinToString(",")}}
            }}}
        """.trimIndent()
    }

    private fun searchResponseJson(vararg ids: String): String =
        """{"search": [${ids.joinToString(",") { """{"id": "$it"}""" }}]}"""

    private fun handleWikidata(
        exchange: HttpExchange,
        searchIds: List<String>,
        entities: Map<String, String>,
    ) {
        val path = exchange.requestURI.path
        if (path == "/w/api.php") {
            respondJson(exchange, 200, searchResponseJson(*searchIds.toTypedArray()))
            return
        }
        val qid = path.removePrefix("/wiki/Special:EntityData/").removeSuffix(".json")
        val entityJson = entities[qid]
        if (entityJson != null) {
            respondJson(exchange, 200, entityJson)
        } else {
            respondJson(exchange, 404, """{"error": "not found"}""")
        }
    }

    @Test
    fun `zero matches yields NoMatch with no candidates`() {
        val wikidata = startServer { exchange -> handleWikidata(exchange, emptyList(), emptyMap()) }
        val outcome = service(wikidata).search("Onbekend Gebouw")

        val noMatch = outcome as PlaceSearchOutcome.NoMatch
        assertTrue(noMatch.candidates.isEmpty())
    }

    @Test
    fun `exactly one direct P131 match builds an answer with P571 and P1435 citations`() {
        val entity = entityDataJson(
            "Q1000",
            locatedIn = PLACE_SEARCH_HEEMSKERK_QID,
            inceptionTime = "+1546-00-00T00:00:00Z",
            heritageQid = "Q916333",
        )
        val heritageLabel = entityDataJson("Q916333", label = "rijksmonument", description = null)
        val wikidata = startServer { exchange ->
            handleWikidata(exchange, listOf("Q1000"), mapOf("Q1000" to entity, "Q916333" to heritageLabel))
        }
        val commons = startServer { exchange -> respondJson(exchange, 200, """{"query": {"pages": {}}}""") }

        val outcome = service(wikidata, commons).search("Kasteel Assumburg") as PlaceSearchOutcome.SupportedAnswer

        assertEquals("Q1000", outcome.answer.qid)
        assertEquals("Kasteel Assumburg", outcome.answer.label)
        assertFalse(outcome.commonsOutage)
        assertTrue(outcome.answer.images.isEmpty())
        val contextSentence = outcome.answer.contextSentence!!
        assertTrue(contextSentence.text.contains("Heemskerk"))
        assertTrue(outcome.answer.sentences.any { it.text.contains("1546") })
        assertTrue(outcome.answer.sentences.any { it.text.contains("rijksmonument") })
        assertEquals(outcome.answer.sources.size, outcome.answer.sentences.size + 1)
        outcome.answer.sources.forEach {
            assertEquals("Q1000", it.qid)
            assertEquals("https://www.wikidata.org/wiki/Q1000", it.wikidataLink)
        }
    }

    @Test
    fun `P131 pointing one level away that itself is located in Heemskerk still matches`() {
        val entity = entityDataJson("Q2000", locatedIn = "Q_DISTRICT")
        val district = entityDataJson("Q_DISTRICT", label = "Wijk", locatedIn = PLACE_SEARCH_HEEMSKERK_QID)
        val wikidata = startServer { exchange ->
            handleWikidata(exchange, listOf("Q2000"), mapOf("Q2000" to entity, "Q_DISTRICT" to district))
        }
        val commons = startServer { exchange -> respondJson(exchange, 200, """{"query": {"pages": {}}}""") }

        val outcome = service(wikidata, commons).search("Iets in een wijk") as PlaceSearchOutcome.SupportedAnswer

        assertTrue(outcome.answer.contextSentence!!.text.contains("Heemskerk"))
    }

    @Test
    fun `a candidate with no P131 but coordinates inside the Heemskerk bounding box matches via P625`() {
        val entity = entityDataJson("Q3000", latitude = 52.51, longitude = 4.67)
        val wikidata = startServer { exchange -> handleWikidata(exchange, listOf("Q3000"), mapOf("Q3000" to entity)) }
        val commons = startServer { exchange -> respondJson(exchange, 200, """{"query": {"pages": {}}}""") }

        val outcome = service(wikidata, commons).search("Coördinaat-kandidaat") as PlaceSearchOutcome.SupportedAnswer

        assertNull(outcome.answer.contextSentence)
    }

    @Test
    fun `coordinates outside the Heemskerk bounding box and no P131 do not match`() {
        val entity = entityDataJson("Q4000", latitude = 52.0, longitude = 4.3)
        val wikidata = startServer { exchange -> handleWikidata(exchange, listOf("Q4000"), mapOf("Q4000" to entity)) }

        val outcome = service(wikidata).search("Ver weg") as PlaceSearchOutcome.NoMatch

        assertTrue(outcome.candidates.isEmpty())
    }

    @Test
    fun `more than one match returns candidate labels as refinement suggestions without merging`() {
        val entityA = entityDataJson("Q5000", label = "Kasteel A", locatedIn = PLACE_SEARCH_HEEMSKERK_QID)
        val entityB = entityDataJson("Q5001", label = "Kasteel B", locatedIn = PLACE_SEARCH_HEEMSKERK_QID)
        val wikidata = startServer { exchange ->
            handleWikidata(exchange, listOf("Q5000", "Q5001"), mapOf("Q5000" to entityA, "Q5001" to entityB))
        }

        val outcome = service(wikidata).search("Kasteel") as PlaceSearchOutcome.NoMatch

        assertEquals(setOf("Kasteel A", "Kasteel B"), outcome.candidates.map { it.label }.toSet())
    }

    @Test
    fun `missing P373 falls back to P18 for a single Commons file lookup`() {
        val entity = entityDataJson("Q6000", locatedIn = PLACE_SEARCH_HEEMSKERK_QID, imageFileName = "Assumburg.jpg")
        val wikidata = startServer { exchange -> handleWikidata(exchange, listOf("Q6000"), mapOf("Q6000" to entity)) }
        val commons = startServer { exchange ->
            val titles = query(exchange)["titles"]
            assertEquals("File:Assumburg.jpg", titles)
            respondJson(
                exchange,
                200,
                """{"query": {"pages": {"1": {"title": "File:Assumburg.jpg", "imageinfo": [
                    {"url": "https://upload.wikimedia.org/Assumburg.jpg", "descriptionurl": "https://commons.wikimedia.org/wiki/File:Assumburg.jpg",
                     "extmetadata": {"LicenseShortName": {"value": "CC BY-SA 4.0"}}}
                ]}}}}""",
            )
        }

        val outcome = service(wikidata, commons).search("Kasteel Assumburg") as PlaceSearchOutcome.SupportedAnswer

        assertEquals(1, outcome.answer.images.size)
        assertEquals("CC BY-SA 4.0", outcome.answer.images.first().license)
        assertFalse(outcome.commonsOutage)
    }

    @Test
    fun `P373 present fetches category members and dedupes and caps at six`() {
        val entity = entityDataJson("Q7000", locatedIn = PLACE_SEARCH_HEEMSKERK_QID, commonsCategory = "Kasteel Assumburg")
        val wikidata = startServer { exchange -> handleWikidata(exchange, listOf("Q7000"), mapOf("Q7000" to entity)) }
        val commons = startServer { exchange ->
            val pages = (1..8).joinToString(",") { i ->
                val name = "File:Assumburg$i.jpg"
                """"$i": {"title": "$name", "imageinfo": [{"url": "https://upload.wikimedia.org/$i.jpg"}]}"""
            }
            respondJson(exchange, 200, """{"query": {"pages": {$pages}}}""")
        }

        val outcome = service(wikidata, commons).search("Kasteel Assumburg") as PlaceSearchOutcome.SupportedAnswer

        assertEquals(6, outcome.answer.images.size)
        assertEquals(6, outcome.answer.images.map { it.fileName }.toSet().size)
    }

    @Test
    fun `a failing wikidata search response is a wikidata outage without any claim`() {
        val wikidata = startServer { exchange -> respondJson(exchange, 500, """{"error": "boom"}""") }

        val outcome = service(wikidata).search("Kasteel Assumburg")

        assertEquals(PlaceSearchOutcome.WikidataOutage, outcome)
    }

    @Test
    fun `invalid json from wikidata is a wikidata outage`() {
        val wikidata = startServer { exchange -> respondJson(exchange, 200, "not json") }

        val outcome = service(wikidata).search("Kasteel Assumburg")

        assertEquals(PlaceSearchOutcome.WikidataOutage, outcome)
    }

    @Test
    fun `a slow wikidata response beyond the overall deadline is a wikidata outage`() {
        val wikidata = startServer { exchange ->
            Thread.sleep(400)
            respondJson(exchange, 200, searchResponseJson("Q1000"))
        }

        val outcome = service(wikidata, deadlineMillis = 100, readTimeoutMillis = 3000).search("Kasteel Assumburg")

        assertEquals(PlaceSearchOutcome.WikidataOutage, outcome)
    }

    @Test
    fun `a failing commons call keeps the wikidata answer but marks commons as an outage`() {
        val entity = entityDataJson("Q8000", locatedIn = PLACE_SEARCH_HEEMSKERK_QID, commonsCategory = "Kasteel Assumburg")
        val wikidata = startServer { exchange -> handleWikidata(exchange, listOf("Q8000"), mapOf("Q8000" to entity)) }
        val commons = startServer { exchange -> respondJson(exchange, 500, """{"error": "boom"}""") }

        val outcome = service(wikidata, commons).search("Kasteel Assumburg") as PlaceSearchOutcome.SupportedAnswer

        assertTrue(outcome.commonsOutage)
        assertTrue(outcome.answer.images.isEmpty())
        assertTrue(outcome.answer.sentences.isNotEmpty())
    }
}
