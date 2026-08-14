package nl.vdzon.hkh.deployment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeploymentTrustBoundaryTest {
    private val repositoryRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("..")

    @Test
    fun `production overlay removes public backend route`() {
        val base = read("deploy/base/kustomization.yaml")

        assertFalse(base.contains("backend-route.yaml"))
        assertFalse(Files.exists(repositoryRoot.resolve("deploy/base/backend-route.yaml")))
    }

    @Test
    fun `backend network policy admits only application proxies`() {
        val policy = read("deploy/base/backend-network-policy.yaml")

        assertTrue(policy.contains("app.kubernetes.io/name: frontend"))
        assertTrue(policy.contains("app.kubernetes.io/name: admin"))
        assertTrue(policy.contains("port: 8080"))
        assertFalse(policy.contains("ipBlock:"))
    }

    @Test
    fun `application proxies replace raw forwarded header with normalized value`() {
        listOf("frontend/nginx.conf", "frontend-admin/nginx.conf").forEach { relativePath ->
            val config = read(relativePath)

            assertTrue(config.contains("proxy_set_header X-Forwarded-For ${'$'}hkh_client_ip;"))
            assertFalse(config.contains("proxy_set_header X-Forwarded-For ${'$'}proxy_add_x_forwarded_for;"))
        }
    }

    private fun read(relativePath: String): String = Files.readString(repositoryRoot.resolve(relativePath))
}
