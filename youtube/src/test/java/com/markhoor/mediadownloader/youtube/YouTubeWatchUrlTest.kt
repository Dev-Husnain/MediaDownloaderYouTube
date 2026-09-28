package com.markhoor.mediadownloader.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every shape a YouTube link arrives in has to come out as the one shape the extractor accepts.
 * This is not cosmetic: a feed card carrying `&list=` is answered "URL not accepted", and on a
 * YouTube feed most cards belong to a playlist or a mix.
 */
class YouTubeWatchUrlTest {

    private val watch = "https://www.youtube.com/watch?v=lRFQ5oXhJeM"

    @Test
    fun `a watch link keeps only the video`() {
        assertEquals(watch, "https://www.youtube.com/watch?v=lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://m.youtube.com/watch?v=lRFQ5oXhJeM&list=PLc15zA9D1iyk".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube.com/watch?v=lRFQ5oXhJeM&pp=0gcJCS8MAYcq#t=30".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube.com/watch?app=desktop&v=lRFQ5oXhJeM".youTubeWatchUrlOrNull())
    }

    @Test
    fun `the short forms name the same video`() {
        assertEquals(watch, "https://youtu.be/lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://youtu.be/lRFQ5oXhJeM?si=BEznK43RXM1WREvd".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube.com/shorts/lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube.com/embed/lRFQ5oXhJeM?autoplay=1".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube.com/live/lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertEquals(watch, "https://www.youtube-nocookie.com/embed/lRFQ5oXhJeM".youTubeWatchUrlOrNull())
    }

    @Test
    fun `a link that names no video is nothing to read`() {
        assertNull("the home feed", "https://m.youtube.com/".youTubeWatchUrlOrNull())
        assertNull("a channel", "https://www.youtube.com/@someone".youTubeWatchUrlOrNull())
        assertNull("a playlist alone", "https://www.youtube.com/playlist?list=PLc15zA9D1iyk".youTubeWatchUrlOrNull())
        assertNull("results", "https://www.youtube.com/results?search_query=cats".youTubeWatchUrlOrNull())
    }

    @Test
    fun `a lookalike host is not youtube`() {
        assertNull("https://notyoutube.com/watch?v=lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertNull("https://youtube.com.evil.test/watch?v=lRFQ5oXhJeM".youTubeWatchUrlOrNull())
        assertNull("https://cdn.test/watch?v=lRFQ5oXhJeM".youTubeWatchUrlOrNull())
    }
}
