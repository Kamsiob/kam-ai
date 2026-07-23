#!/usr/bin/env bash
# Fetches whisper.cpp at the pinned tag into app/src/main/cpp/whisper.cpp.
#
# Like llama.cpp, whisper.cpp is fetched rather than committed to keep this
# repository small. It is built into its own shared library (libkamwhisper.so)
# with its own copy of ggml, kept separate from llama.cpp's library so the two
# copies of ggml never collide at link time. The tag below is the single source
# of truth for the version Kam AI builds against. Bump it here, rebuild, test,
# and note the change in DECISIONS.md.

set -euo pipefail

WHISPER_TAG="v1.7.6"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$repo_root/app/src/main/cpp/whisper.cpp"

if [ -d "$dest/.git" ] || [ -f "$dest/CMakeLists.txt" ]; then
  current="$(cat "$dest/.kamai-tag" 2>/dev/null || echo unknown)"
  if [ "$current" = "$WHISPER_TAG" ]; then
    echo "whisper.cpp $WHISPER_TAG is already in place."
    exit 0
  fi
  echo "Replacing whisper.cpp $current with $WHISPER_TAG."
  rm -rf "$dest"
fi

echo "Fetching whisper.cpp $WHISPER_TAG..."
git clone --depth 1 --branch "$WHISPER_TAG" \
  https://github.com/ggml-org/whisper.cpp.git "$dest"

rm -rf "$dest/.git"
echo "$WHISPER_TAG" > "$dest/.kamai-tag"

echo "whisper.cpp $WHISPER_TAG is ready at $dest"
