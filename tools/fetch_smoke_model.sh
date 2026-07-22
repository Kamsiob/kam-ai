#!/usr/bin/env bash
# Puts the Phase 0 smoke test model in place as a test asset.
#
# It is a 1.2 MB story model, far too small to say anything sensible. The smoke
# test uses it to prove the native path works, not to judge output quality. It
# is fetched rather than committed so the repository stays free of binaries.

set -euo pipefail

MODEL_URL="https://huggingface.co/ggml-org/models/resolve/main/tinyllamas/stories260K.gguf"
MODEL_NAME="stories260K.gguf"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest_dir="$repo_root/app/src/androidTest/assets"
dest="$dest_dir/$MODEL_NAME"

mkdir -p "$dest_dir"

if [ -s "$dest" ]; then
  echo "Smoke model already in place at $dest"
  exit 0
fi

echo "Fetching the smoke test model..."
curl -fsSL -o "$dest" "$MODEL_URL"
echo "Smoke model ready at $dest"
