package nl.vdzon.hkh.historicalsearch

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            val kustomization = Files.readString(root.resolve("deploy/overlays/$overlay/kustomization.yaml"))
            assertTrue(kustomization.contains("- ../../base"), "$overlay moet de gedeelde base erven")
            assertFalse(
                kustomization.contains("hkh-open-archieven-config") ||
                    kustomization.contains("HKH_HISTORICAL_OPEN_ARCHIEVEN_"),
                "$overlay mag het canonieke Open Archieven-contract niet overschrijven",
            )
        }

        val applicationProperties = Files.readString(root.resolve("backend/src/main/resources/application.properties"))
        canonical.keys.filter { it.startsWith("HKH_HISTORICAL_OPEN_ARCHIEVEN_") }.forEach { envName ->
            assertTrue(applicationProperties.contains(envName), "$envName moet door de backend worden gelezen")
        }
    }

    private fun repositoryRoot(): Path {
        var path = Path.of("").toAbsolutePath()
        while (!Files.exists(path.resolve("deploy/base/open-archieven-config.yaml"))) {
            path = path.parent ?: error("Repository root met de deploymentconfiguratie niet gevonden")
        }
        return path
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
}
