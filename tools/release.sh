#!/usr/bin/env bash
#
# Cut a release. A release is a git tag - the build takes its version from it - but the README also
# prints a number for people to copy, and that is the part that goes stale. So it is written here:
#
#   tools/release.sh 0.1.1 "what changed"
#
# It refuses to run unless the tree is clean and on main, builds and tests first, then rewrites the
# add-on's own coordinate wherever the docs print it, commits, tags and pushes both. The library's
# coordinate is deliberately left alone: the README states it as a minimum ("0.1.2 or newer"), not
# as the version to take.
set -euo pipefail

version=${1:-}
message=${2:-}
if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "usage: tools/release.sh <major.minor.patch> [tag message]" >&2
    exit 1
fi

cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
    echo "the working tree is not clean; commit or stash first" >&2
    exit 1
fi
if [[ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]]; then
    echo "releases are cut from main, not $(git rev-parse --abbrev-ref HEAD)" >&2
    exit 1
fi
if git rev-parse -q --verify "refs/tags/$version" >/dev/null; then
    echo "$version already exists. A tag is final on JitPack - use the next patch number." >&2
    exit 1
fi

echo "== building and testing"
./gradlew :youtube:testDebugUnitTest :youtube:assembleRelease

echo "== saying $version in the docs"
sed -i "s|MediaDownloaderYouTube:[0-9][0-9.]*|MediaDownloaderYouTube:$version|g" README.md CLAUDE.md
if git diff --quiet; then
    echo "   (already up to date)"
else
    git commit -aqm "Say $version in the install instructions"
fi

echo "== tagging and pushing"
git tag -a "$version" -m "${message:-$version}"
git push origin main
git push origin "$version"

cat <<NOTE

Pushed. JitPack builds on the first request for the artifact, so ask for it once:

  curl -s -o /dev/null -w "%{http_code}\n" \
    https://jitpack.io/com/github/Dev-Husnain/MediaDownloaderYouTube/$version/MediaDownloaderYouTube-$version.pom

200 means it is being served. The end of
https://jitpack.io/com/github/Dev-Husnain/MediaDownloaderYouTube/$version/build.log
prints the coordinate it actually published - read that rather than assuming.

If this release exists because the library gained something, check that the README's "needs the
library at X or newer" line still tells the truth.
NOTE
