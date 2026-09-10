package nl.vdzon.hkh.topicsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

class TopicSearchWikidataContextClientTest {

    private var server: HttpServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (HttpExchange) -> Unit): TopicSearchWikidataContextClient {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        httpServer.start()
        server = httpServer
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(1000)
            setReadTimeout(3000)
        }
        val restClient = RestClient.builder()
            .baseUrl("http://localhost:${httpServer.address.port}")
            .requestFactory(requestFactory)
            .build()
        return TopicSearchWikidataContextClient(restClient)
    }

    private fun respondJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handle(exchange: HttpExchange, searchIds: List<String>, entityJson: Map<String, String>) {
        val path = exchange.requestURI.path
        if (path == "/w/api.php") {
            respondJson(exchange, 200, """{"search": [${searchIds.joinToString(",") { """{"id": "$it"}""" }}]}""")
            return
        }
        val qid = path.removePrefix("/wiki/Special:EntityData/").removeSuffix(".json")
        val json = entityJson[qid]
        if (json != null) respondJson(exchange, 200, json) else respondJson(exchange, 404, """{"error": "not found"}""")
    }

    @Test
    fun `exactly one candidate builds a context block`() {
        val entity = """{"entities": {"Q1": {"labels": {"nl": {"value": "Watersnood van 1916"}}, "descriptions": {"nl": {"value": "overstroming"}}}}}"""
        val client = startServer { exchange -> handle(exchange, listOf("Q1"), mapOf("Q1" to entity)) }

        val context = client.fetchContext("watersnood van 1916")

        assertEquals("Watersnood van 1916", context?.label)
        assertEquals("overstroming", context?.description)
    }

    @Test
    fun `zero candidates yields no context`() {
        val client = startServer { exchange -> handle(exchange, emptyList(), emptyMap()) }

        assertNull(client.fetchContext("onbekend"))
    }

    @Test
    fun `more than one candidate yields no context`() {
        val client = startServer { exchange -> handle(exchange, listOf("Q1", "Q2"), emptyMap()) }

        assertNull(client.fetchContext("ambigu"))
    }

    @Test
    fun `a wikidata failure never throws and yields no context`() {
        val client = startServer { exchange -> respondJson(exchange, 500, """{"error": "boom"}""") }

        assertNull(client.fetchContext("watersnood van 1916"))
    }
}
