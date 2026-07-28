#!/usr/bin/env bash
# Fetches ARM's KleidiAI sources into app/src/main/cpp/kleidiai.
#
# Same reasoning as fetch_llama.sh: vendored rather than committed, pinned to one
# version, and that version is the single source of truth here.
#
# **Why this script exists at all.** ggml can fetch KleidiAI itself, but it does
# so at CMake configure time, from inside the Android Gradle build. When that
# download does not happen, the wrappers still compile, the kai_* symbols are
# never defined, and the failure arrives as a link error a long way from its
# cause. Fetching it up front makes the dependency explicit and the build
# reproducible offline, and it fails here, plainly, rather than there.
#
# The tag and the checksum are the ones ggml itself pins, read out of
# ggml/src/ggml-cpu/CMakeLists.txt. If llama.cpp is bumped and that file changes
# them, change them here too, or the build will use a KleidiAI that ggml was not
# written against.

set -euo pipefail

KLEIDIAI_TAG="v1.24.0"
KLEIDIAI_MD5="2f02ebe29573d45813e671eb304f2a00"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$repo_root/app/src/main/cpp/kleidiai"
url="https://github.com/ARM-software/kleidiai/releases/download/${KLEIDIAI_TAG}/kleidiai-${KLEIDIAI_TAG}-src.tar.gz"

if [ -f "$dest/.kamai-tag" ] && [ "$(cat "$dest/.kamai-tag")" = "$KLEIDIAI_TAG" ]; then
  echo "KleidiAI $KLEIDIAI_TAG is already in place."
  exit 0
fi

# Check against what ggml pins, so a bumped llama.cpp cannot silently pair a new
# ggml with an old KleidiAI.
ggml_cmake="$repo_root/app/src/main/cpp/llama.cpp/ggml/src/ggml-cpu/CMakeLists.txt"
if [ -f "$ggml_cmake" ]; then
  want="$(grep -oE 'KLEIDIAI_COMMIT_TAG "[^"]+"' "$ggml_cmake" | head -1 | cut -d'"' -f2 || true)"
  if [ -n "$want" ] && [ "$want" != "$KLEIDIAI_TAG" ]; then
    echo "ggml pins KleidiAI $want but this script pins $KLEIDIAI_TAG." >&2
    echo "Update KLEIDIAI_TAG and KLEIDIAI_MD5 in this script to match." >&2
    exit 1
  fi
fi

rm -rf "$dest"
mkdir -p "$dest"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Fetching KleidiAI $KLEIDIAI_TAG..."
curl -sSL --max-time 600 -o "$tmp/kleidiai.tar.gz" "$url"

actual="$(md5sum "$tmp/kleidiai.tar.gz" | cut -d' ' -f1)"
if [ "$actual" != "$KLEIDIAI_MD5" ]; then
  echo "Checksum mismatch for KleidiAI $KLEIDIAI_TAG." >&2
  echo "  expected $KLEIDIAI_MD5" >&2
  echo "  got      $actual" >&2
  exit 1
fi

# The archive holds a single top-level directory; strip it so the layout matches
# what FETCHCONTENT_SOURCE_DIR expects, which is the source root itself.
tar -xzf "$tmp/kleidiai.tar.gz" -C "$dest" --strip-components=1
echo "$KLEIDIAI_TAG" > "$dest/.kamai-tag"

echo "KleidiAI $KLEIDIAI_TAG is in $dest."
