# MediaDownloaderYouTube

[![JitPack](https://jitpack.io/v/Dev-Husnain/MediaDownloaderYouTube.svg)](https://jitpack.io/#Dev-Husnain/MediaDownloaderYouTube)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A YouTube reader for [MediaDownloaderLibrary](https://github.com/Dev-Husnain/MediaDownloaderLibrary),
built on [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).

It is four files. The library does everything else — the sheet, the sizes, the download engine that
fetches the picture and the sound and joins them, the browser that finds a card on a feed, the
playlist that queues itself. This only answers the question the library cannot: *what is behind this
YouTube link?*

---

## Read this before you use it

**This is GPLv3 and the library is not.** NewPipeExtractor is GPLv3, and anything built against it
is covered by it. Add this to an app and **that whole app must be released under the GPL**, source
included. That is exactly why it is a separate repository: the library stays Apache-2.0, and an app
that does not add this one dependency line is not touched by any of it.

**Google Play removes apps that download from YouTube.** NewPipe itself is not on Play for this
reason. Whatever the licence says, this does not belong in a Play build. It is also against
YouTube's terms of service, which is a separate thing from the licence and is between you and them.

**It breaks.** YouTube changes what it serves; NewPipeExtractor keeps up, this repository may not.
It is an experiment kept working well enough to learn from, not a product.

---

## Adding it

Two dependencies and one line of config.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }   // both of the below are served from here
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.Dev-Husnain:MediaDownloaderLibrary:0.1.2")
    implementation("com.github.Dev-Husnain:MediaDownloaderYouTube:0.1.0")
}
```

```kotlin
// Application.onCreate
MediaDownloader.initialize(
    this,
    MediaDownloaderConfig(
        // The library blocks YouTube outright without this. Keep it false in anything you publish.
        allowYouTube = true,
        extraSources = listOf(YouTubeSource()),
    ),
)
```

The badge at the top of this page always shows this repository's newest release; if it reads
higher than the line above, take the badge's number.

That is the whole integration. It needs the library at **0.1.2 or newer** — that is the version
where `MediaSource` and `MediaCollectionSource` exist. Everything in the
[library's guide](https://github.com/Dev-Husnain/MediaDownloaderLibrary/blob/main/media_downloader/HOST-GUIDE.md)
then applies to YouTube links unchanged.

R8 keep rules ship with it (`consumer-rules.pro`): Rhino runs YouTube's player javascript and
reaches for its classes reflectively, so a minified build would otherwise fail at extraction time
rather than at build time.

---

## Using it

Nothing here is called directly — the library asks it. What follows is what the library's own API
does once this source is in place.

### A link a reader pasted or shared

```kotlin
when (val parsed = MediaDownloader.read(pastedText).getOrThrow()) {
    is ParsedLink.One -> showQualities(parsed.media)          // a video or a short
    is ParsedLink.Many -> showPlaylist(parsed.collection)     // a playlist
}
```

Every shape is understood: `youtube.com/watch?v=ID`, `youtu.be/ID`, `/shorts/ID`, `/embed/ID`,
`/live/ID`, and watch links with playlists, mixes, `&si=` and other tracking parameters hanging off
them. All are reduced to the video's own id before the extractor sees them — it refuses anything
else, and a feed card almost always carries `&list=`.

`LinkParseViewModel` does the same for a screen: `LinkParseUiState.Success` for a video,
`LinkParseUiState.Collection` for a playlist.

### Downloading one video

```kotlin
MediaDownloader.download(media, media.qualities.first())
```

Above 360p YouTube keeps the picture and the sound in separate files. The library's engine fetches
both and joins them into one MP4 — nothing to do here.

### A playlist

```kotlin
MediaDownloader.downloadCollection(collection, preferredQuality = "720p")
```

The library queues **every entry at once**, each as its own download, and reads each video's file
when its turn comes:

- one folder named after the playlist,
- files numbered `01 - …`, `02 - …` in the playlist's own order,
- the quality asked for, or the closest an entry actually offers,
- the entries not started yet visible as queued downloads, under their own names.

`pauseCollection(title)` and `resumeCollection(title)` stop and start the whole thing;
`DownloadModel.collectionTitle` is what a screen groups them by. A `/playlist?list=…` link is a
playlist; a watch link that merely carries `&list=` is still one video — that is somebody watching
from inside a playlist, not asking for it.

### The browser

The library's browser tab needs nothing extra. With this source in place a YouTube feed draws a
download button on each card, pressing one reads that card's video **without leaving the feed**, and
the Shorts feed and the shorts shelf work the same way.

---

## What works, and what does not

**Works**

- Videos, shorts and playlists (up to 200 entries), from a link or from a feed.
- Title, thumbnail, running time, and the quality ladder with sizes.
- 1080p and above, by pairing each video-only stream with the best AAC audio; the library joins them.

**Does not, and why**

| | |
|---|---|
| **Only H.264 video and AAC audio** | The join writes an MP4 through `MediaMuxer`, which takes nothing else. A VP9 or Opus track downloads perfectly and then cannot be written — that is how a 480p download once came out silent. Resolutions YouTube serves only as VP9 or AV1 are therefore not offered. |
| **No livestreams** | A broadcast still running has no end and no size; it is refused rather than offered as a video nothing can state a length for. |
| **Some videos offer 360p only** | YouTube answers the default client with a single progressive file for them. Asking as the iOS client returns the whole ladder — and those urls are **403 on every device**, because they are bound to the client that asked. A quality that cannot be downloaded is worth less than one fewer quality. |
| **Nothing that needs an account** | Members-only, age-gated, private. |

---

## How it works

YouTube's web player asks for its media through a server-driven exchange (`sabr=1`) instead of one
url per format, so the library's usual trick — watching what the player fetches — has nothing to
take. An extractor asks as a different client, which is still answered with a url per format, and
getting those urls means solving the signature and the `n` parameter by running YouTube's own player
javascript. NewPipeExtractor does that, and keeps doing it as YouTube changes; that is the part
nobody wants to maintain by hand, and the reason for the licence.

| file | what it is |
|---|---|
| `YouTubeSource.kt` | the `MediaSource` + `MediaCollectionSource` the library is given: hosts, link canonicalisation, format rules |
| `YouTubePlaylist.kt` | a playlist read as a list of links, paged |
| `NewPipeDownloader.kt` | the network side the extractor asks its host to provide, on `HttpURLConnection` |
| `consumer-rules.pro` | keep rules, because Rhino and the extractor both work reflectively |

**Size:** about **1.6 MB** on an app's APK — Rhino 1.27 MB (the javascript engine), the extractor
802 KB, jsoup 513 KB, nanojson and rhino-engine 45 KB, once dexed and compressed.

## Where the shape of this came from

[`FINDINGS.md`](FINDINGS.md) is the working record: what YouTube actually served when it was
measured on a device, why sniffing the player cannot work, why only H.264 and AAC are offered, what
the iOS client promised and why it was dropped, and the four separate faults that stood between a
feed card's button and the video behind it. Read it before changing anything here - most of what
looks like a missing feature is a measurement.

## Building it

```bash
./gradlew :youtube:assembleRelease :youtube:testDebugUnitTest
```

To build against a library you are changing rather than a released one, publish it locally
(`./gradlew :media_downloader:publishToMavenLocal` in the library's repo) and point
`gradle/libs.versions.toml` at what git called it:

```toml
mediaDownloader = "0.1.1-7-gc66957a"   # whatever `git describe` says
media-downloader = { module = "com.github.Dev-Husnain.MediaDownloaderLibrary:media_downloader", version.ref = "mediaDownloader" }
```

`mavenLocal()` is already first in `settings.gradle.kts`. The coordinate differs from the JitPack
one because JitPack serves a single-artifact repository under the repository's own name.

A release here is a git tag and nothing else; JitPack builds it (`jitpack.yml`: JDK 21).

## License

Copyright 2026 Hussnain Mehdi

Licensed under the [GNU General Public License v3.0](LICENSE), because NewPipeExtractor is. See the
warning at the top: this licence reaches every app that includes this.
