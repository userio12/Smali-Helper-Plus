package com.riyadm.smalihelperplus.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Executes the call asynchronously; cancelling the coroutine cancels the HTTP call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }
        },
    )
}

/**
 * Reads at most [maxBytes] from the source. Returns null when the body is larger than the limit (the remaining bytes
 * are never buffered).
 */
fun BufferedSource.readBytesLimited(maxBytes: Long): ByteArray? {
    val buffer = Buffer()
    var total = 0L
    while (true) {
        val read = read(buffer, 8192L)
        if (read == -1L) break
        total += read
        if (total > maxBytes) return null
    }
    return buffer.readByteArray()
}
