package nl.vdzon.hkh.historicalsearch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.time.Duration
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that the production and acceptance overlays consume one non-secret Open Archieven
 * contract. The overlays inherit the base ConfigMap; an overlay-local override would invalidate
 * this test instead of silently creating deployment drift.
 */
class Hkh195OpenArchievenConfigurationContractTest {
    @Test
    fun `production and acceptance use the canonical endpoint search contract`() {
        val root = repositoryRoot()
        val canonical = parseConfigMap(root.resolve("deploy/base/open-archieven-config.yaml"))

        assertEquals(
            mapOf(
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_BASE_URL" to "https://api.openarchieven.nl/1.1",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_SEARCH_PATH" to "/records/search.json",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_NAME_PARAMETER" to "name",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_EVENTPLACE_PARAMETER" to "eventplace",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_NUMBER_SHOW_PARAMETER" to "number_show",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_START_PARAMETER" to "start",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_ARCHIVE_CODE_PARAMETER" to "archive_code",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_HEEMSKERK_ARCHIVE_CODE" to "hee",
            ),
            canonical.filterKeys { it.contains("PARAMETER") || it.endsWith("BASE_URL") || it.endsWith("SEARCH_PATH") || it.endsWith("ARCHIVE_CODE") },
            "endpoint, zoekpad en parameter-mapping moeten exact het canonieke contract vormen",
        )
        assertEquals(
            mapOf(
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_TIMEOUT" to "10s",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_CACHE_DURATION" to "30s",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_RATE_LIMIT_INTERVAL" to "251ms",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_BUDGET_PER_MINUTE" to "60",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_BUDGET_BURST_CAPACITY" to "10",
                "HKH_HISTORICAL_OPEN_ARCHIEVEN_BUDGET_REFILL_PER_SECOND" to "1.0",
            ),
            canonical.filterKeys { it.contains("TIMEOUT") || it.contains("CACHE") || it.contains("RATE_LIMIT") || it.contains("BUDGET") },
            "bestaande timeout-, cache-, rate-limit- en budgetinstellingen mogen niet verschillen",
        )

        val endpoint = URI(canonical.getValue("HKH_HISTORICAL_OPEN_ARCHIEVEN_BASE_URL"))
        assertEquals("https", endpoint.scheme)
        assertTrue(endpoint.query == null && endpoint.fragment == null)
        assertTrue(canonical.values.all { !it.contains("?") && !it.contains("#") })
        assertTrue(canonical.keys.none { it.contains("SECRET") || it.contains("TOKEN") || it.contains("PASSWORD") })

        val baseKustomization = Files.readString(root.resolve("deploy/base/kustomization.yaml"))
        assertTrue(baseKustomization.contains("- open-archieven-config.yaml"))
        val deployment = Files.readString(root.resolve("deploy/base/backend-deployment.yaml"))
        assertTrue(deployment.contains("name: hkh-open-archieven-config"))

        listOf("openshift", "acceptance").forEach { overlay ->
            val rendered = renderKustomization(root.resolve("deploy/overlays/$overlay"))
            val effective = parseRenderedConfigMap(rendered, "hkh-open-archieven-config")
            assertEquals(canonical, effective, "$overlay moet de effectieve canonieke ConfigMap gebruiken")
        }

        val applicationProperties = Files.readString(root.resolve("backend/src/main/resources/application.properties"))
        canonical.keys.filter { it.startsWith("HKH_HISTORICAL_OPEN_ARCHIEVEN_") }.forEach { envName ->
            assertTrue(applicationProperties.contains(envName), "$envName moet door de backend worden gelezen")
        }
    }

    @Test
    fun `open archieven request keeps versioned base path`() {
        val requestedPath = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestedPath.set(exchange.requestURI.toString())
            val response = if (exchange.requestURI.path == "/1.1/records/search.json") {
                """{"response":{"docs":[{"source_name":"Heemskerk archief","uuid":"fixture-uuid","original_source_url":"https://example.test/record/fixture-uuid"}],"number_found":1}}"""
            } else {
                "{}"
            }
            val status = if (exchange.requestURI.path == "/1.1/records/search.json") 200 else 404
            respond(exchange, status, response)
        }
        server.start()

        try {
            val configuration = HistoricalSearchConfiguration(
                europeanaBaseUrl = "https://example.test/europeana",
                europeanaWskey = "",
                openArchievenBaseUrl = "http://127.0.0.1:${server.address.port}/1.1",
                openArchievenSearchPath = "/records/search.json",
                openArchievenNameParameter = "name",
                openArchievenEventplaceParameter = "eventplace",
                openArchievenNumberShowParameter = "number_show",
                openArchievenStartParameter = "start",
                openArchievenArchiveCodeParameter = "archive_code",
                openArchievenHeemskerkArchiveCode = "hee",
                openArchievenTimeout = Duration.ofSeconds(1),
                openArchievenCacheDuration = Duration.ofSeconds(30),
                openArchievenRateLimitInterval = Duration.ofMillis(251),
                openArchievenBudgetPerMinute = 60,
                openArchievenBudgetBurstCapacity = 10,
                openArchievenBudgetRefillPerSecond = 1.0,
                trustedProxyAddresses = "",
            )
            val adapter = configuration.openArchievenSearchAdapter(HistoricalSearchRateLimiter { })

            val page = adapter.search(HistoricalSearchQuery(text = "Heemskerk"))

            assertEquals(HistoricalTechnicalStatus.AVAILABLE, page.status)
            assertEquals("/1.1/records/search.json", URI(requestedPath.get()).path)
            assertTrue(URI(requestedPath.get()).query.orEmpty().contains("archive_code=hee"))
        } finally {
            server.stop(0)
        }
    }

    private fun repositoryRoot(): Path {
        var path = Path.of("").toAbsolutePath()
        while (!Files.exists(path.resolve("deploy/base/open-archieven-config.yaml"))) {
            path = path.parent ?: error("Repository root met de deploymentconfiguratie niet gevonden")
        }
        return path
    }

    private fun renderKustomization(directory: Path): String {
        val process = try {
            ProcessBuilder("kubectl", "kustomize", directory.toString())
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            error("kubectl kustomize is vereist om de effectieve overlayconfiguratie te controleren")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) {
            error("kubectl kustomize kon overlay niet renderen")
        }
        return output
    }

    private fun parseRenderedConfigMap(rendered: String, name: String): Map<String, String> {
        val documents = rendered.split(Regex("(?m)^---\\s*$"))
        val document = documents.firstOrNull { block ->
            Regex("(?m)^kind: ConfigMap\\s*$").containsMatchIn(block) &&
                Regex("(?m)^  name: ${Regex.escape(name)}\\s*$").containsMatchIn(block)
        } ?: error("Gerenderde ConfigMap $name ontbreekt")
        return document.lineSequence()
            .dropWhile { it.trim() != "data:" }
            .drop(1)
            .takeWhile { it.startsWith("  ") }
            .filter { it.startsWith("  ") && !it.startsWith("    ") && !it.trimStart().startsWith("#") }
            .associate { line ->
                val separator = line.indexOf(':')
                require(separator > 2) { "Ongeldige gerenderde ConfigMap-regel" }
                line.substring(2, separator) to line.substring(separator + 1).trim().trim('"')
            }
    }

    private fun parseConfigMap(path: Path): Map<String, String> = Files.readAllLines(path)
        .dropWhile { !it.trim().equals("data:") }
        .drop(1)
        .takeWhile { it.startsWith("  ") }
        .filterNot { it.trimStart().startsWith("#") }
        .associate { line ->
            val separator = line.indexOf(':')
            require(separator > 2) { "Ongeldige ConfigMap-regel: $line" }
            val key = line.substring(2, separator)
            val value = line.substring(separator + 1).trim().trim('"')
            key to value
        }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
