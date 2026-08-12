package nl.vdzon.hkh.externalverification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class OpenArchievenMetadataAdapterTest {
    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `a valid fixture returns the complete metadata contract`() {
        var receivedUserAgent: String? = null
        val client = startServer { exchange ->
            receivedUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            respond(exchange, 200, validFixture("v1"))
        }

        val result = client.fetch("1000", "item-1")

        assertEquals(HistoricalMetadataVerificationStatus.VERIFIED, result.verificationStatus)
        assertEquals(HistoricalMetadataVerificationReasons.VERIFIED, result.verificationReason)
        assertEquals("http://opendata.archieven.nl/id/1000/item-1", result.sourceLink)
        assertEquals("https://opendata.archieven.nl/id/1000/item-1", result.metadata?.sourceIdentifier)
        assertEquals("Historical Kring Heemskerk", result.metadata?.holder)
        assertEquals("Kaart van Heemskerk", result.metadata?.title)
        assertEquals("1900", result.metadata?.dating)
        assertEquals("v1", result.metadata?.sourceVersion)
        assertEquals(Instant.parse("2026-08-12T14:00:00Z"), result.fetchedAt)
        assertEquals(MetadataRightsStatus.ALLOWED, result.metadataRightsStatus)
        assertEquals(ObjectMediaRightsStatus.UNKNOWN, result.objectMediaRightsStatus)
        assertTrue(result.fullyVerified)
        assertFalse(result.mediaAllowed)
        assertEquals("HKH-Autopilot-HistoricalMetadata/1.0", receivedUserAgent)
    }

    @Test
    fun `unknown object rights do not invalidate metadata but never allow media`() {
        val client = startServer { exchange -> respond(exchange, 200, validFixture("v1")) }

        val result = client.fetch("1000", "item-1")

        assertTrue(result.fullyVerified)
        assertEquals(ObjectMediaRightsStatus.UNKNOWN, result.objectMediaRightsStatus)
        assertFalse(result.mediaAllowed)
    }

    @Test
    fun `unknown metadata rights fail closed without returning title or description`() {
        val client = startServer { exchange ->
            respond(exchange, 200, validFixture("v1").replace("\"ALLOWED\"", "\"UNKNOWN\""))
        }

        val result = client.fetch("1000", "item-1")

        assertEquals(HistoricalMetadataVerificationStatus.UNVERIFIED, result.verificationStatus)
        assertEquals(HistoricalMetadataVerificationReasons.METADATA_RIGHTS_UNKNOWN, result.verificationReason)
        assertNull(result.metadata)
        assertEquals("1000/item-1", result.sourceIdentifier)
        assertTrue(result.sourceLink!!.startsWith("http://opendata.archieven.nl/id/"))
    }

    @Test
    fun `personal-data marker is redacted even when the source claims clear privacy`() {
        val client = startServer { exchange ->
            respond(
                exchange,
                200,
                validFixture("v1").replace("\"privacyStatus\": \"CLEAR\"", "\"privacyStatus\": \"CLEAR\", \"personalData\": true"),
            )
        }

        val result = client.fetch("1000", "item-1")

        assertEquals(HistoricalMetadataVerificationReasons.PRIVACY_BLOCKED, result.verificationReason)
        assertNull(result.metadata)
    }

    @Test
    fun `temporary source outage and empty response are both minimal and unverified`() {
        var mode = 503
        val body = AtomicReference("")
        val client = startServer { exchange ->
            if (mode == 503) respond(exchange, 503, "{}") else respond(exchange, 200, body.get())
        }

        val outage = client.fetch("1000", "item-1")
        assertEquals(HistoricalMetadataAvailabilityStatus.TEMPORARILY_UNAVAILABLE, outage.availabilityStatus)
        assertEquals(HistoricalMetadataVerificationReasons.SOURCE_TEMPORARILY_UNAVAILABLE, outage.verificationReason)
        assertNull(outage.metadata)

        mode = 200
        val empty = client.fetch("1000", "item-1")
        assertEquals(HistoricalMetadataAvailabilityStatus.EMPTY_RESPONSE, empty.availabilityStatus)
        assertEquals(HistoricalMetadataVerificationReasons.EMPTY_SOURCE_RESPONSE, empty.verificationReason)
        assertNull(empty.metadata)
    }

    @Test
    fun `a changed source version is returned on the next fetch and is not cached`() {
        var version = "v1"
        val client = startServer { exchange -> respond(exchange, 200, validFixture(version)) }

        val first = client.fetch("1000", "item-1")
        version = "v2"
        val second = client.fetch("1000", "item-1")

        assertEquals("v1", first.metadata?.sourceVersion)
        assertEquals("v2", second.metadata?.sourceVersion)
    }

    @Test
    fun `the limiter schedules requests at least 251 milliseconds apart per server`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter = FourPerSecondRateLimiter(
            nowNanos = { now },
            sleepNanos = { nanos -> waits += nanos; now += nanos },
        )

        repeat(5) { limiter.awaitPermit("fixture-server") }

        assertEquals(4, waits.size)
        assertTrue(waits.all { it >= 251_000_000L })
    }

    private fun startServer(handler: (HttpExchange) -> Unit): HistoricalMetadataAdapter {
        val newServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        newServer.createContext("/") { exchange -> handler(exchange) }
        newServer.start()
        server = newServer
        val restClient = RestClient.builder().baseUrl("http://localhost:${newServer.address.port}").build()
        return OpenArchievenMetadataAdapter(
            restClient = restClient,
            serverKey = "fixture-server-${newServer.address.port}",
            clock = Clock.fixed(Instant.parse("2026-08-12T14:00:00Z"), ZoneOffset.UTC),
        )
    }

    private fun validFixture(version: String) =
        """{
          "@id": "https://opendata.archieven.nl/id/1000/item-1",
          "title": "Kaart van Heemskerk",
          "publisher": "Historical Kring Heemskerk",
          "date": "1900",
          "version": "$version",
          "metadataRightsStatus": "ALLOWED",
          "objectMediaRightsStatus": "UNKNOWN",
          "privacyStatus": "CLEAR"
        }"""

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/ld+json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
