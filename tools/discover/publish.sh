#!/usr/bin/env bash
# Publishes the built Discover packs and manifest as assets on a GitHub release.
#
#   python3 tools/discover/build_packs.py     # build first
#   tools/discover/publish.sh                 # then publish
#
# GitHub release assets have no bandwidth limits and a 2 GiB per-file cap, so this
# serves any number of users at no cost. Rerunning replaces the assets in place.

set -euo pipefail

TAG="discover-packs-v1"
REPO="Kamsiob/kam-ai"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="$here/out"

if [ ! -f "$out/manifest.json" ]; then
  echo "No built packs. Run build_packs.py first." >&2
  exit 1
fi

assets=("$out"/*.kampack "$out/manifest.json")

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "Updating existing release $TAG"
  gh release upload "$TAG" "${assets[@]}" --repo "$REPO" --clobber
else
  echo "Creating release $TAG"
  gh release create "$TAG" "${assets[@]}" --repo "$REPO" \
    --title "Discover content packs" \
    --notes "Offline Discover content packs built from English Wikipedia (CC BY-SA 4.0). Downloaded in-app; nothing about the user is sent. Rebuilt by tools/discover/build_packs.py."
fi

echo "Published $TAG"
