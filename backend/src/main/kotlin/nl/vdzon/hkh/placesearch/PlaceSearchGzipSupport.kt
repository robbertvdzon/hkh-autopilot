package nl.vdzon.hkh.placesearch

import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * Eigen kopie van het gzip-interceptorpatroon uit `personsearch` (`GzipRequestInterceptor`): deze
 * module heeft `allowedDependencies = {}` en mag dus niet op `personsearch` steunen. Vraagt
 * `Accept-Encoding: gzip` op en zet een beschrijvende User-Agent-header; decomprimeert transparant
 * een gzip-gecodeerde respons.
 */
class PlaceSearchGzipRequestInterceptor(private val userAgent: String) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip")
        request.headers.set(HttpHeaders.USER_AGENT, userAgent)
        val response = execution.execute(request, body)
        val isGzip = response.headers.getFirst(HttpHeaders.CONTENT_ENCODING)?.equals("gzip", ignoreCase = true) == true
        return if (isGzip) GzipDecodingClientHttpResponse(response) else response
    }
}

private class GzipDecodingClientHttpResponse(private val delegate: ClientHttpResponse) : ClientHttpResponse by delegate {
    override fun getBody(): InputStream = GZIPInputStream(delegate.body)
}
