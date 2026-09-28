package com.markhoor.mediadownloader.youtube

import com.markhoor.mediadownloader.domain.models.MediaCollectionItem
import com.markhoor.mediadownloader.domain.models.MediaCollectionModel
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * A YouTube playlist, read as a list of links.
 *
 * Nothing here fetches a video: what comes back is what the playlist says it holds, in its own
 * order. The library reads each video for its qualities when its turn to be downloaded comes,
 * which is also what keeps the urls fresh - YouTube mints them for whoever asked and they go stale
 * within hours, so a fifty-video playlist read up front would expire before its end was reached.
 */
internal object YouTubePlaylist {

    /**
     * How many entries are taken from a playlist.
     *
     * A playlist is paged, and YouTube's cap is five thousand; every page is a request, and a
     * reader who asked for a playlist did not ask to sit through a hundred of them. Two hundred is
     * past anything a person queues by hand.
     */
    private const val MAX_ITEMS = 200

    /**
     * A playlist link - `/playlist?list=…`, on any of YouTube's hosts.
     *
     * A watch link that merely carries `&list=` is **not** one: that is a video being watched from
     * inside a playlist, and a reader who pressed it wants that video. It is read as a single video
     * with its playlist parameter dropped.
     */
    fun isPlaylistLink(url: String): Boolean {
        val host = url.youTubeHostOrNull() ?: return false
        if (host == "youtu.be") return false
        val path = url.pathOf()
        return path == "playlist" && url.listIdOf() != null
    }

    /** What the playlist lists, or `null` when it turns out to hold nothing. */
    fun read(url: String): MediaCollectionModel? {
        YouTubeSource.ensureExtractorReady()
        val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()

        val items = mutableListOf<MediaCollectionItem>()
        var page: ListExtractor.InfoItemsPage<StreamInfoItem>? = extractor.initialPage
        while (page != null && items.size < MAX_ITEMS) {
            page.items.forEach { item -> item.asCollectionItem()?.let(items::add) }
            page = page.nextPageOrNull()?.let { next -> extractor.getPage(next) }
        }
        if (items.isEmpty()) return null

        return MediaCollectionModel(
            title = extractor.name.ifBlank { "YouTube playlist" },
            sourceUrl = url,
            items = items.take(MAX_ITEMS),
        )
    }

    private fun ListExtractor.InfoItemsPage<StreamInfoItem>.nextPageOrNull(): Page? =
        if (hasNextPage()) nextPage else null

    /**
     * One entry, or `null` when it is not a video anybody can fetch: a playlist keeps its deleted
     * and private entries in place, named "[Deleted video]", with a url that leads nowhere.
     */
    private fun StreamInfoItem.asCollectionItem(): MediaCollectionItem? {
        val link = url.orEmpty().takeIf { it.isNotBlank() && it.youTubeWatchUrlOrNull() != null }
            ?: return null
        return MediaCollectionItem(
            url = link,
            title = name.orEmpty(),
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
            durationMillis = duration.takeIf { it > 0 }?.times(1_000),
        )
    }
}

/** The path of a url without its leading slash, query or fragment: `playlist`, `watch`, `shorts/ID`. */
internal fun String.pathOf(): String =
    substringAfter("://", "").substringAfter('/', "").substringBefore('?').substringBefore('#')

/** The `list=` parameter, when there is one that reads like a playlist id. */
internal fun String.listIdOf(): String? = substringAfter("list=", "").substringBefore('&')
    .takeIf { it.length in 2..64 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
