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

    @Test
    fun `android release uses the public frontend proxy instead of the removed backend route`() {
        val workflow = read(".github/workflows/build-apk.yml")
        val deploymentReadme = read("deploy/README.md")

        assertTrue(
            workflow.contains(
                "--dart-define=API_BASE_URL=https://hkh-autopilot.vdzonsoftware.nl \\",
            ),
        )
        assertFalse(workflow.contains("--dart-define=API_BASE_URL=${'$'}{{ vars.API_BASE_URL }}"))
        assertTrue(deploymentReadme.contains("twee TLS-routes"))
    }

    private fun read(relativePath: String): String = Files.readString(repositoryRoot.resolve(relativePath))
}
