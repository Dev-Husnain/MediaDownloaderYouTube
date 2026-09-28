package com.markhoor.mediadownloader.youtube

import com.markhoor.mediadownloader.MediaCollectionSource
import com.markhoor.mediadownloader.MediaSource
import com.markhoor.mediadownloader.domain.models.MediaCollectionModel
import com.markhoor.mediadownloader.domain.models.MediaModel
import com.markhoor.mediadownloader.domain.models.MediaQualityModel
import com.markhoor.mediadownloader.domain.models.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat as ExtractorFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Reads YouTube videos for MediaDownloaderLibrary, through NewPipeExtractor.
 *
 * Give it to the library once, at startup:
 *
 * ```kotlin
 * MediaDownloader.initialize(
 *     this,
 *     MediaDownloaderConfig(
 *         allowYouTube = true,              // the library blocks YouTube without this
 *         extraSources = listOf(YouTubeSource()),
 *     ),
 * )
 * ```
 *
 * From then on a YouTube link is read like any other: the sheet lists its qualities, the engine
 * fetches the picture and the sound and joins them, and a card on a YouTube feed hands over that
 * video's own page instead of the feed's.
 *
 * **Why an extractor at all**, when the library reads every other site by sniffing what its player
 * fetches: YouTube's web player asks for its media through a server-driven exchange (`sabr=1`)
 * rather than one url per format, so there is nothing in the traffic to take. An extractor asks as
 * a different client, which is still answered with per-format urls - and keeps working through
 * YouTube's changes, which is the part nobody wants to maintain by hand.
 *
 * **This is GPLv3 and the library is not.** Shipping an app with this in it puts that app under the
 * GPL. Google Play also removes apps that download from YouTube. Read the README before using it.
 *
 * @param ioDispatcher where the extractor's blocking network calls run.
 */
class YouTubeSource(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaSource, MediaCollectionSource {

    override val hosts: Set<String> = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /**
     * Only links that name one video. YouTube is mostly not videos - the home feed, a channel, a
     * search, the subscriptions page - and claiming those would tell the library that a feed is a
     * page holding one video: it would then draw a single button on the feed instead of one per
     * card, and a press would hand over the feed's own url.
     */
    override fun handles(url: String): Boolean = url.youTubeWatchUrlOrNull() != null

    /** A playlist link, which is read as a list rather than as one video. See [YouTubePlaylist]. */
    override fun handlesCollection(url: String): Boolean = YouTubePlaylist.isPlaylistLink(url)

    /**
     * What the playlist lists, in its own order. Only the entries are read here - each video is
     * read for its qualities by the library when its turn to be downloaded comes.
     */
    override suspend fun readCollection(url: String): MediaCollectionModel? =
        withContext(ioDispatcher) { YouTubePlaylist.read(url) }

    override suspend fun read(url: String): MediaModel? = withContext(ioDispatcher) {
        ensureExtractorReady()
        // The extractor is given the video's own url, never the one the page linked to. It refuses
        // anything it does not read as a single video - a feed card carrying "&list=" comes back as
        // "URL not accepted", and on a YouTube feed most cards belong to a playlist or a mix.
        val watchUrl = url.youTubeWatchUrlOrNull() ?: return@withContext null
        val extractor = ServiceList.YouTube.getStreamExtractor(watchUrl)
        extractor.fetchPage()

        // A broadcast that is still running has no end and therefore no download: what it offers is
        // the window it is serving right now. Measured on the feed, the sheet filled with a "1.1 MB"
        // 1080p and a 720p of unknown size - neither is the video, because there is no video yet.
        if (extractor.streamType == StreamType.LIVE_STREAM ||
            extractor.streamType == StreamType.AUDIO_LIVE_STREAM
        ) {
            return@withContext null
        }

        // The sound for every video-only stream. YouTube keeps high resolutions apart from their
        // audio; the library's download engine fetches both and joins them.
        val audio: AudioStream? = extractor.audioStreams
            // AAC, not Opus. The join writes an MP4 through MediaMuxer, which takes AAC; an Opus
            // track downloads happily and then cannot be written, leaving a silent file.
            .filter { it.usable() && it.format == ExtractorFormat.M4A }
            .maxByOrNull { it.averageBitrate }

        // Streams that carry their own sound. YouTube only offers these up to 360p these days,
        // but they need no joining, so they are worth keeping.
        val progressive = extractor.videoStreams
            .filter { it.usable() }
            .map { stream -> quality(stream.content, stream.label()) }

        val videoOnly = extractor.videoOnlyStreams
            // H.264 in MP4 for the same reason: VP9/WebM will not go into the container either.
            .filter { it.usable() && it.format == ExtractorFormat.MPEG_4 && audio != null }
            .sortedByDescending { it.height }
            .map { stream -> quality(stream.content, stream.label(), audio?.content) }

        val qualities = (videoOnly + progressive).distinctBy { it.label }
        if (qualities.isEmpty()) return@withContext null

        MediaModel(
            title = extractor.name.orEmpty(),
            thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url,
            qualities = qualities,
            sourceUrl = url,
            durationMillis = extractor.length.takeIf { it > 0 }?.times(1_000),
        )
    }

    private fun quality(url: String, label: String, audioUrl: String? = null) = MediaQualityModel(
        url = url,
        label = label,
        type = MediaType.Video,
        audioUrl = audioUrl,
    )

    /** A stream the library can fetch: a url, not a manifest or a segment list. */
    private fun Stream.usable(): Boolean = isUrl && content.isNotBlank()

    /**
     * What to call this quality - `1080p60` when the extractor names it, the height otherwise.
     *
     * Through `getResolution()`, not `resolution`: the class exposes both a public field and a
     * getter, Kotlin's property syntax resolves to the field, and the field is the deprecated half.
     */
    private fun VideoStream.label(): String = getResolution().ifBlank { "${height}p" }

    internal companion object {
        @Volatile
        private var ready = false

        /** `NewPipe.init` is global and must run once before any extractor is made. */
        @Synchronized
        fun ensureExtractorReady() {
            if (ready) return
            NewPipe.init(NewPipeDownloader())
            /* The default client, deliberately. Asking as the iOS client
               (`YoutubeStreamExtractor.setFetchIosClient(true)`) answers with a fuller ladder -
               some videos come back from the default one with a single 360p file and no adaptive
               streams at all - but its urls cannot be fetched: measured on a device, every one of
               them is 403 whatever is sent with it, user agent included, because they are bound to
               the client that asked. A quality that cannot be downloaded is worth less than one
               fewer quality. */
            ready = true
        }
    }
}

/**
 * The plain watch url of the video this link names, or `null` when it names none.
 *
 * Every shape a link can arrive in - `youtu.be/ID`, `/shorts/ID`, `/embed/ID`, `/live/ID`, a watch
 * link with a playlist, a mix and tracking parameters hanging off it - is reduced to the same
 * `watch?v=ID`, because that is the only shape the extractor accepts.
 */
internal fun String.youTubeWatchUrlOrNull(): String? {
    val host = youTubeHostOrNull() ?: return null
    val path = substringAfter("://", "").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val id = when {
        host == "youtu.be" -> path.substringBefore('/')
        path.startsWith("shorts/") || path.startsWith("embed/") || path.startsWith("live/") ->
            path.substringAfter('/').substringBefore('/')
        else -> substringAfter("v=", "").substringBefore('&').substringBefore('#')
    }
    val readable = id.length in 6..24 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    return if (readable) "https://www.youtube.com/watch?v=$id" else null
}

/**
 * The host of a url, lowercased and without `www.`, or `null` when there is none.
 *
 * The library keeps its own copy of this and does not expose it, and one function is not worth a
 * dependency on anything else.
 */
private fun String.normalizedHost(): String? = runCatching {
    java.net.URI(trim()).host?.lowercase()?.removePrefix("www.")?.ifBlank { null }
}.getOrNull()

/**
 * The host of a YouTube url, or `null` when the url is somebody else's.
 *
 * A host is YouTube's when it is one of these or a subdomain of one. `endsWith` alone is not that
 * test: "notyoutube.com" ends with "youtube.com" and belongs to somebody else.
 */
internal fun String.youTubeHostOrNull(): String? {
    val host = normalizedHost() ?: return null
    val youTube = listOf("youtu.be", "youtube.com", "youtube-nocookie.com")
        .any { host == it || host.endsWith(".$it") }
    return host.takeIf { youTube }
}
