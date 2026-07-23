#!/usr/bin/env bash
# Fetches the sherpa-onnx prebuilt Android runtime into app/src/main/jniLibs.
#
# sherpa-onnx is the text-to-speech runtime. Its native libraries (the JNI
# wrapper and onnxruntime) are large prebuilt binaries, so like llama.cpp and
# whisper.cpp they are fetched rather than committed. Only the arm64-v8a
# libraries are kept, since that is the only ABI Kam AI ships. The Kotlin API
# (Tts.kt) is vendored in the source tree because it is small and readable; only
# the binaries are fetched here. The tag below is the single source of truth.

set -euo pipefail

SHERPA_TAG="v1.13.4"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$repo_root/app/src/main/jniLibs/arm64-v8a"
marker="$dest/.kamai-sherpa-tag"

if [ -f "$marker" ] && [ "$(cat "$marker")" = "$SHERPA_TAG" ] \
    && [ -f "$dest/libsherpa-onnx-jni.so" ] && [ -f "$dest/libonnxruntime.so" ]; then
    echo "sherpa-onnx $SHERPA_TAG is already in place."
    exit 0
fi

url="https://github.com/k2-fsa/sherpa-onnx/releases/download/${SHERPA_TAG}/sherpa-onnx-${SHERPA_TAG}-android.tar.bz2"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Fetching sherpa-onnx $SHERPA_TAG..."
curl -sL -o "$tmp/sherpa.tar.bz2" "$url"

echo "Extracting arm64-v8a libraries..."
tar xjf "$tmp/sherpa.tar.bz2" -C "$tmp" \
    ./jniLibs/arm64-v8a/libsherpa-onnx-jni.so \
    ./jniLibs/arm64-v8a/libonnxruntime.so

mkdir -p "$dest"
cp "$tmp/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" "$dest/"
cp "$tmp/jniLibs/arm64-v8a/libonnxruntime.so" "$dest/"
echo "$SHERPA_TAG" > "$marker"

# The Piper voices share one espeak-ng phonemiser data set and one tokens file.
# Rather than download them with every voice, they are bundled once in the app
# assets: espeak-ng-data zipped into a single file, and the shared tokens file.
# Only the per-voice model.onnx is downloaded at runtime.
assets="$repo_root/app/src/main/assets"
if [ ! -f "$assets/espeak-ng-data.zip" ] || [ ! -f "$assets/piper-tokens.txt" ]; then
    echo "Preparing shared Piper voice data..."
    piper_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2"
    curl -sL -o "$tmp/piper.tar.bz2" "$piper_url"
    tar xjf "$tmp/piper.tar.bz2" -C "$tmp"
    base="$tmp/vits-piper-en_US-amy-low"
    mkdir -p "$assets"
    ( cd "$base" && zip -q -r -X "$assets/espeak-ng-data.zip" espeak-ng-data )
    cp "$base/tokens.txt" "$assets/piper-tokens.txt"
fi

echo "sherpa-onnx $SHERPA_TAG is ready at $dest"
