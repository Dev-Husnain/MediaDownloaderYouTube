# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`:youtube` (package `com.markhoor.mediadownloader.youtube`) — a YouTube reader for
**MediaDownloaderLibrary**, kept in its own repo *only* because
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) is **GPLv3** and that obligation
travels with distribution. This repo is therefore **GPL-3.0**; the library it extends stays
Apache-2.0 and knows nothing about YouTube. There is no app and no UI here: one Android library
module, minSdk 24, compileSdk 37, Java 21.

`README.md` is the host-facing document (warnings, install, use, limits, how it works, size) and
`FINDINGS.md` records what YouTube actually served while this was built — read `FINDINGS.md` before
changing extraction; it explains the choices that look wrong.

**Play Store policy forbids shipping YouTube downloading.** This exists to learn from and to test
with, not for a released app. Keep that in anything written here.

## Commands

```bash
./gradlew :youtube:assembleDebug
./gradlew :youtube:testDebugUnitTest
./gradlew :youtube:publishToMavenLocal     # for a local consumer
```

`settings.gradle.kts` puts `mavenLocal()` before JitPack, so a locally published library build wins
while one is being worked on. When testing a local round, bump to a **fresh** version each time —
Gradle will serve a stale mavenLocal artifact and make a real fix look like it did nothing.

## The two repos

| | repo | licence | coordinate |
|---|---|---|---|
| library | `Dev-Husnain/MediaDownloaderLibrary` (`D:\Other Data\DownloaderLibByHussnain\MediaDownloaderLibrary`) | Apache-2.0 | `com.github.Dev-Husnain:MediaDownloaderLibrary:0.1.2` |
| this add-on | `Dev-Husnain/MediaDownloaderYouTube` | GPL-3.0 | `com.github.Dev-Husnain:MediaDownloaderYouTube:0.1.0` |

The dependency points **this repo → the library**, never the reverse. Nothing GPL may be added to
the library; if something is needed there, it goes in as a *hook* the host fills, not as code.
JitPack serves either repo under its repo name and caches a tag, so a bad publish needs a new tag.

## How it plugs in

`YouTubeSource` implements the library's two public host hooks and is registered in
`MediaDownloaderConfig` by the app:

- `MediaSource` — `hosts`, `handles(url)` (`youTubeWatchUrlOrNull() != null`, which also
  canonicalises `&list=` watch links NewPipe would otherwise reject), `read(url): MediaModel?`.
- `MediaCollectionSource` (`YouTubePlaylist`) — `handlesCollection`/`readCollection`, paged, capped
  at `MAX_ITEMS`. The library then queues the whole playlist and resolves each video's URL when its
  turn comes.

Deliberate, not oversights:

- **Default client only.** The iOS client yields more qualities but its URLs are client-bound and
  403 on a real device — a quality that cannot be downloaded is worth less than one fewer quality.
- **Formats filtered to `M4A` + `MPEG_4`** because MediaMuxer only accepts H.264 + AAC for MP4.
- **`StreamType.LIVE_STREAM` is refused.**
- `VideoStream.label()` uses `getResolution()`; the *field* is deprecated, the getter is not.
- `consumer-rules.pro` keeps the Rhino/extractor classes reflection reaches (YouTube's signature and
  `n` parameter are solved by running JS).
- Build note: `buildscript { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }` in the
  root is what matches the library's Kotlin metadata under AGP 9's built-in Kotlin.

## Working agreements

- Commits and tags are authored by **Hussnain Mehdi** alone, over the SSH remote. No AI/assistant
  attribution or co-author lines in commit messages.
- Releases are cut by git tag; nothing else sets the version (`gitDescribe` fills it locally). Use
  `tools/release.sh <version> "what changed"` - it refuses a dirty tree, a branch other than `main`
  and an existing tag, tests and assembles, rewrites this add-on's coordinate wherever the docs
  print it, then tags and pushes. A tag is final on JitPack: a bad release needs the next patch
  number, not a re-cut. The library's coordinate in the README is a *minimum* and is left alone.
