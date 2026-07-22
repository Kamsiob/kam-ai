#!/usr/bin/env bash
# Fetches llama.cpp at the pinned tag into app/src/main/cpp/llama.cpp.
#
# llama.cpp is fetched rather than committed: it is around 160 MB of source and
# this repository stays small and readable without it. The tag below is the
# single source of truth for which version Kam AI builds against. Bump it here,
# rebuild, test, and note the change in DECISIONS.md.

set -euo pipefail

LLAMA_TAG="b10058"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$repo_root/app/src/main/cpp/llama.cpp"

if [ -d "$dest/.git" ] || [ -f "$dest/CMakeLists.txt" ]; then
  current="$(cat "$dest/.kamai-tag" 2>/dev/null || echo unknown)"
  if [ "$current" = "$LLAMA_TAG" ]; then
    echo "llama.cpp $LLAMA_TAG is already in place."
    exit 0
  fi
  echo "Replacing llama.cpp $current with $LLAMA_TAG."
  rm -rf "$dest"
fi

echo "Fetching llama.cpp $LLAMA_TAG..."
git clone --depth 1 --branch "$LLAMA_TAG" \
  https://github.com/ggml-org/llama.cpp.git "$dest"

rm -rf "$dest/.git"
echo "$LLAMA_TAG" > "$dest/.kamai-tag"

echo "llama.cpp $LLAMA_TAG is ready at $dest"
