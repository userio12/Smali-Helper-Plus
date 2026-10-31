package com.riyadm.smalihelperplus.data.updates

import com.riyadm.smalihelperplus.data.net.await
import com.riyadm.smalihelperplus.data.net.readBytesLimited
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Downloads raw bytes with a hard size limit. Throws [UpdateException] for every failure. */
interface UpdateFetcher {
    suspend fun fetch(url: String, maxBytes: Long): ByteArray
}

/**
 * OkHttp-backed fetcher. Only HTTPS is accepted, redirects that change the scheme are not followed, responses larger
 * than [maxBytes] are aborted while streaming, and nothing downloaded is ever executed.
 *
 * [allowCleartext] exists only so unit tests can use a plain-HTTP MockWebServer; the Hilt constructor always passes false.
 */
class OkHttpUpdateFetcher internal constructor(
    client: OkHttpClient,
    private val allowCleartext: Boolean,
) : UpdateFetcher {

    @Inject constructor(client: OkHttpClient) : this(client, false)

    private val http: OkHttpClient = client.newBuilder().followSslRedirects(false).build()

    override suspend fun fetch(url: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val httpUrl = ManifestValidator.requireHttpsUrl(url, requireHttps = !allowCleartext)
        val request = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "SmaliHelperPlus-ContentUpdater")
            .build()
        try {
            http.newCall(request).await().use { response ->
                if (!allowCleartext && !response.request.url.isHttps) {
                    throw UpdateException(UpdateError.InsecureUrl("the server redirected to an insecure address"))
                }
                if (!response.isSuccessful) throw UpdateException(UpdateError.Http(response.code))
                val declared = response.body.contentLength()
                if (declared > maxBytes) throw UpdateException(UpdateError.TooLarge)
                return@withContext response.body.source().readBytesLimited(maxBytes) ?: throw UpdateException(UpdateError.TooLarge)
            }
        } catch (e: InterruptedIOException) {
            throw UpdateException(UpdateError.Timeout, e)
        } catch (e: IOException) {
            throw UpdateException(UpdateError.Offline, e)
        }
    }
}
