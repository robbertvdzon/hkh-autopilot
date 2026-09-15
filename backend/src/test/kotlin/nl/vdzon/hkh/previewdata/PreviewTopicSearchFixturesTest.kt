package nl.vdzon.hkh.previewdata

import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import kotlin.test.*
import org.springframework.web.server.ResponseStatusException

class PreviewTopicSearchFixturesTest {
    @Test
    fun `synthetic HTTP response passes through the real Europeana client and record mapper`() {
        val fixtures = PreviewTopicSearchFixtures(PreviewRuntimeConfig(true, PreviewRuntimeConfig.REQUIRED_MARKER, "jdbc:postgresql://database:5432/hkh", "42"))
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("localhost", 0), 0)
        server.createContext("/test-fixtures/europeana/record/v2/search.json") { exchange ->
            val query = java.net.URLDecoder.decode(exchange.requestURI.rawQuery, "UTF-8")
            val bytes = mapper.writeValueAsBytes(fixtures.search(query).body)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val client = nl.vdzon.hkh.topicsearch.RestClientArchivesEuropeanaClient(
                org.springframework.web.client.RestClient.builder().baseUrl("http://localhost:${server.address.port}/test-fixtures/europeana").build(), "preview-fixture-only")
            val result = client.search("Heemskerk") as nl.vdzon.hkh.topicsearch.EuropeanaSearchOutcome.Success
            assertEquals("Preview-testcollectie", result.validRecords.single().dataProvider)
            assertEquals("https://example.invalid/europeana/preview-1", result.validRecords.single().sourceUrl)
            assertTrue((client.search("test-leeg") as nl.vdzon.hkh.topicsearch.EuropeanaSearchOutcome.Success).validRecords.isEmpty())
        } finally { server.stop(0) }
    }

    @Test
    fun `fixtures refuse production and standing acceptance`() {
        assertFailsWith<IllegalArgumentException> { PreviewTopicSearchFixtures(PreviewRuntimeConfig(false, "", "", "")) }
        assertFailsWith<IllegalArgumentException> { PreviewTopicSearchFixtures(PreviewRuntimeConfig(true, PreviewRuntimeConfig.ACCEPTANCE_MARKER, "jdbc:postgresql://database:5432/hkh", "")) }
    }

    @Test
    fun `preview offers success empty malformed and upstream failure scenarios`() {
        val fixtures = PreviewTopicSearchFixtures(PreviewRuntimeConfig(true, PreviewRuntimeConfig.REQUIRED_MARKER, "jdbc:postgresql://database:5432/hkh", "42"))
        val normal = fixtures.search("Heemskerk").body as Map<*, *>
        assertEquals(1, normal["totalResults"])
        assertEquals(0, (fixtures.search("test-leeg").body as Map<*, *>)["totalResults"])
        assertEquals("invalid-json", fixtures.search("test-ongeldige-json").body)
        assertEquals(503, assertFailsWith<ResponseStatusException> { fixtures.search("test-storing") }.statusCode.value())
    }
}
