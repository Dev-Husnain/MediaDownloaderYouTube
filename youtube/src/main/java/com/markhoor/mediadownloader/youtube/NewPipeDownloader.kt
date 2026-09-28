package com.markhoor.mediadownloader.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * The network side NewPipeExtractor asks the host to provide.
 *
 * Built on `HttpURLConnection` rather than a client of its own: the extractor's contract is one
 * blocking call that hands back a whole body, and there is nothing here worth a networking
 * dependency for. It runs on [YouTubeSource]'s IO dispatcher.
 */
internal class NewPipeDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            request.headers().forEach { (name, values) ->
                // Compression is left to the connection: it only unzips what it asked for itself,
                // and a body the extractor cannot read is worse than an uncompressed one.
                if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                    values.forEach { value -> addRequestProperty(name, value) }
                }
            }
        }
        return try {
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(
                code,
                connection.responseMessage.orEmpty(),
                // The header map carries the status line under a null key, which the extractor's
                // own lookups do not expect.
                connection.headerFields.filterKeys { it != null },
                text,
                connection.url.toString(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
