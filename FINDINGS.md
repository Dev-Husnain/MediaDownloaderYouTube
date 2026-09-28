# YouTube: what detection actually sees

Measured 25 and 28 September 2026 on a Samsung SM-A266B, with MediaDownloaderLibrary's demo app and
`allowYouTube = true`.

**This is the working record behind this repository**: what was tried, what the device actually
answered, and why the code ended up the shape it is. It was written while the reader lived on a
branch of the library; it now lives here, with the code it describes. The one thing that has moved
on is the last section - reading YouTube is no longer an experiment kept off to one side, it is
this repository, taken as `com.github.Dev-Husnain:MediaDownloaderYouTube`.

The question was narrow: **does the detection this library already has - the request sniffer and the
page script, the same machinery every other site uses - produce anything downloadable on YouTube?**

## What was measured

| | Mobile client (`m.youtube.com`, WebView's own UA) | Desktop client (`www.youtube.com`, desktop UA) |
|---|---|---|
| Site access with the flag on | `Allowed` - the block lifts as designed | same |
| `<video>` element | 1, `src` = `blob:https://m.youtube.com/…` | same |
| Media requests once playing | 5, all to `…googlevideo.com` | 10, same |
| `sabr` parameter | **`1`** | **`1`** |
| `itag` / `mime` / `clen` / `range` | **none present** | **none present** |
| Page script | drew its button (38x38) on the player | same |
| Pressing it | "Looking for media…", then nothing | same |

## What that means

A `blob:` url is a MediaSource handle, not a file: there is nothing in the DOM to download.

The player's media requests carry `sabr=1`. That is YouTube's server-driven ABR: the client sends a
request **body** describing what it wants and the server answers with a multiplexed stream, rather
than serving one file per format at a URL you can GET. The absence of `itag`, `mime`, `clen` and
`range` is the same story from the other side - those are the fields a per-format URL would carry,
and there is no per-format URL any more.

So the sniffer sees the traffic and can take nothing from it. This is not a gap in the library: the
mechanism that works everywhere else does not apply here. Changing the user agent does not help -
both clients are served SABR.

## What would take a file

Two things could get one, and both are the same kind of work:

1. **Speak the client protocol** - build the SABR request body and parse the multiplexed response.
2. **Ask as a different client** - some clients are still answered with one url per format. Getting
   those urls out of the player response means solving the signature and the `n` parameter by running
   YouTube's own player JavaScript.

They are the reason apps that do support YouTube keep a **server** doing the work:
the extraction breaks whenever YouTube changes its player, and a server can be updated without
shipping an app. That pattern is already in this library for other sites (`tikwm` for TikTok,
`tweeload` for X) - for YouTube it would mean running that service yourself, and the terms and legal
exposure would sit with whoever runs it.

Separately, and regardless of how it is done: **Google Play removes apps that download from
YouTube.** `allowYouTube` stays `false` in anything published there. A competitor's listing being
live today says nothing about tomorrow, and such listings avoid naming YouTube for exactly that
reason.

## What this branch then did

Route 2, through **NewPipeExtractor** - which keeps solving the signature and the `n` parameter as
YouTube changes them, and is a great deal of upkeep to do by hand. It is two files,
`data/scraper/youtube/YouTubeScraper.kt` and a `NewPipeDownloader` bridge for its network calls, and
it is a `SiteScraper` like every other site: nothing else in the module knows it is special. The
component builds it **only** when `allowYouTube` is on, so a build with the flag off does not carry
the path at all.

It works. On the device, `youtu.be/ihysL1K4blc` read its title, duration and thumbnail, offered
1080p down to 144p, and the 1080p download came out as `ftypmp42`, two traks, `avc1` + `mp4a`,
34,570,526 bytes, 511.7 seconds - a file that plays with sound. From the home feed, pressing a
card's button downloaded that card's video without leaving the feed: 43,709,573 bytes, again two
traks, `avc1` + `mp4a`.

### What the feed needed on top of that

A video's own page worked straight away. The home feed did not, and it took four separate fixes,
each measured on the device:

| what happened | why | what changed |
|---|---|---|
| The card opened the video instead of downloading | YouTube acts on the press from a handler high in the document, and capture runs top-down - the button's own listeners, which stop everything they see, ran too late | presses are taken at the `window`, above every page handler, and only ever for this script's own buttons |
| "Looking for media…", then nothing | the press handed over `m.youtube.com` - the feed itself. The `<video>` preview is laid *over* the card rather than inside it, so no walk upwards ever reaches the card's anchor | the card is asked of the layout when the walk finds none: the link painted under the media's own box |
| …and when it did find the anchor, it refused it | a card link had to read like a title or an id, and YouTube's is `/watch` with the id in the query | on a site the parser reads, one path segment plus a query counts as a post |
| The link named a playlist | feed cards carry `&list=`, and the extractor answers "URL not accepted" | every link is reduced to `watch?v=ID` before the extractor sees it |

The press also had no answer of its own while the parse ran - five seconds of nothing, which reads
as a press that missed. A disc now turns over the button that was pressed, and the app says when it
has stopped looking (`window.mksSearchDone`), so it is never left turning.

### A dead end: the iOS client

Some videos come back from the default client with a single 360p file and no adaptive streams at all
(`CNOfAicD7Is`: one `MPEG_4/360p`, no audio streams, no video-only streams). Asking as the iOS
client - `YoutubeStreamExtractor.setFetchIosClient(true)` - answers with the whole ladder, audio
included, and the sheet fills with 1080p down to 144p.

Those urls cannot be fetched. On the device every one of them is **403**, with or without the iOS
user agent, first request or parallel range; on a desktop they serve 206 happily, which is what
makes it look like it works. They are bound to the client that asked for them. A quality that cannot
be downloaded is worth less than one fewer quality, so the default client stays.

### The container decides which resolutions can be offered

Above 360p YouTube keeps picture and sound in separate files, so those downloads go through the
engine's merge path, which writes an MP4 with `MediaMuxer`. That container takes **AAC and H.264 and
nothing else**. An Opus or VP9 track downloads perfectly and then cannot be written, and the file
lands silent - which is exactly what the first 480p attempt did before the filters went in.

So the scraper keeps only `M4A` audio and `MPEG_4` video-only streams. The price is real: the
resolutions YouTube serves as VP9 or AV1 only are not listed at all, and the streams that carry their
own sound stop at 360p.

### What it costs in size

The demo's debug APK, built twice in the same worktree with nothing different but this dependency and
the scraper:

| | bytes |
|---|---|
| without | 15,826,662 |
| with | 17,481,048 |
| **added** | **1,654,386 (1.6 MiB)** |

Where it goes, as jars: Rhino 1.27 MB - the JavaScript engine that runs YouTube's player code -
NewPipeExtractor 802 KB, jsoup 513 KB (the module's own scrapers never needed it), nanojson 31 KB,
rhino-engine 14 KB. Unminified; R8 would take some of it back, though Rhino reaches for its classes
reflectively and would need keep rules to survive.

### Why this is a repository of its own

NewPipeExtractor is GPLv3, and anything built against it is covered by it. Inside the library that
would put every app using the library under the GPL. Here it puts nothing under anything until an
app adds this one dependency, and the library stays Apache-2.0 - which is the whole reason this is
a separate repository rather than a folder in that one.

## If it is revisited

Re-run the measurement before writing any code - two commands, and it is the only thing that
settles whether the picture has changed:

```js
// in the page, once the video is playing
performance.getEntriesByType('resource')
    .map(e => e.name).filter(n => n.includes('googlevideo'))
```

If those URLs ever carry `itag` and `clen` again and no `sabr`, plain sniffing becomes possible and
this file is out of date.
