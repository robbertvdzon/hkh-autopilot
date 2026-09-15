package nl.vdzon.hkh.previewdata

import nl.vdzon.hkh.auth.PreviewRuntimeConfig
import kotlin.test.*
import org.springframework.web.server.ResponseStatusException

class PreviewTopicSearchFixturesTest {
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
