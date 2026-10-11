#!/usr/bin/env bash
# Build the RailOne Morphe patch bundle (patches/build/libs/patches-*.mpp).
#
# Requires:
#   - JDK 21  (installed at ~/.local/jdk21; the Morphe Gradle plugin is strict about 21)
#   - a GitHub token with read:packages, because the Morphe Gradle plugin and the
#     morphe-patcher library live on GitHub Packages (maven.pkg.github.com/MorpheApp/registry)
#
# Get the scope once with:  gh auth refresh -h github.com -s read:packages
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

cd "$(dirname "$0")"

# ./gradlew cannot bootstrap itself here: the wrapper's Java downloader times out following
# services.gradle.org's 307 redirect to GitHub releases, while curl fetches the same file at
# ~17 MB/s. Gradle 9.8.0 is therefore pre-extracted at ~/.local/gradle and used directly.
GRADLE_BIN="${GRADLE_BIN:-$HOME/.local/gradle/gradle-9.8.0/bin/gradle}"
if [[ ! -x "$GRADLE_BIN" ]]; then
    echo "!! $GRADLE_BIN not found; falling back to ./gradlew" >&2
    GRADLE_BIN="./gradlew"
fi

export GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login)}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token)}"

echo "JAVA:    $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "GRADLE:  $GRADLE_BIN"
echo "ANDROID: $ANDROID_HOME"
echo "ACTOR:   $GITHUB_ACTOR"

# Order and forcing matter. :patches:generatePatchesList depends on :patches:build -> :patches:jar,
# which rewrites patches-<version>.mpp as a plain class jar; :patches:buildAndroid is the task that
# compiles the patches to DEX and merges classes.dex into that same file. Two consequences:
#   - the Android Morphe Manager needs classes.dex and refuses a bundle without it
#     ("IllegalStateException: Patch bundle is missing dex entries" in its log)
#   - the desktop CLI does NOT catch this, because on a JVM it loads the .class files happily
# buildAndroid tracks its own outputs and reports UP-TO-DATE even when jar has just overwritten the
# bundle with a dex-less one, so it must be forced with --rerun; deleting the file is not enough.
rm -f patches/build/libs/patches-*.mpp
"$GRADLE_BIN" --no-daemon :patches:generatePatchesList
"$GRADLE_BIN" --no-daemon :patches:buildAndroid --rerun

echo
echo "=== bundle ==="
ls -lh patches/build/libs/*.mpp
echo
# NOTE: `unzip -l a.mpp b.mpp` lists *members* named b.mpp inside a.mpp - always test one file.
BUNDLE="$(ls patches/build/libs/patches-*.mpp | grep -v -e '-sources.mpp' -e '-javadoc.mpp' | head -1)"
if unzip -l "$BUNDLE" | grep -q "classes\.dex"; then
    echo "OK: $BUNDLE contains classes.dex (loadable by Morphe Manager on Android)"
else
    echo "!! $BUNDLE has NO classes.dex - the Android Manager will refuse it. Re-run buildAndroid." >&2
    exit 1
fi
