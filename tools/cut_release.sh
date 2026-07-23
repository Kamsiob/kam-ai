#!/usr/bin/env bash
# Cuts a Kam AI release: builds the signed bundle and a signed universal APK,
# publishes the APK on a GitHub release, and (with --play) uploads the bundle to
# the Play internal track through the Android Publisher API. Then downloads the
# published APK back and verifies its SHA-256.
#
# Usage:
#   tools/cut_release.sh            # build + GitHub APK release
#   tools/cut_release.sh --play     # also upload the AAB to Play (internal track)
#
# Every release publishes two artifacts and this is not optional (see DECISIONS):
# the Play bundle, and a plain APK on GitHub for people who avoid the Play Store.

set -euo pipefail

REPO="Kamsiob/kam-ai"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
export JAVA_HOME="${JAVA_HOME:-/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec}"

do_play=false
[ "${1:-}" = "--play" ] && do_play=true

version="$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -1)"
tag="v$version"
apk_name="kam-ai-$version.apk"
echo "Releasing $tag"

if [ ! -f "$HOME/.kamsiob-secrets/keystore.properties" ]; then
  echo "Upload keystore not found. See LAUNCH.md." >&2
  exit 1
fi

echo "Building signed bundle and APK..."
./gradlew --quiet :app:bundleRelease :app:assembleRelease

aab="app/build/outputs/bundle/release/app-release.aab"
apk="app/build/outputs/apk/release/app-release.apk"
cp "$apk" "$apk_name"
sha="$(sha256sum "$apk_name" | cut -d' ' -f1)"
echo "APK sha256: $sha"

notes="$(cat <<EOF
Kam AI $version.

A private, fully-local AI on your phone. It runs on your device, so your
conversations never leave it.

Install:
- Play Store: the usual way, updates automatically.
- This APK: for people who avoid the Play Store or run a de-Googled phone. It is
  the same version.

Note on the two versions: the GitHub APK and the Play Store build are signed
differently, so Android treats them as separate apps. To switch between them,
uninstall one before installing the other, and carry your conversations across
with the app's own Backup and restore (Settings, Backup and restore).

SHA-256 of this APK: $sha
EOF
)"

echo "Publishing GitHub release..."
if gh release view "$tag" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$tag" "$apk_name" --repo "$REPO" --clobber
else
  git tag -f "$tag" >/dev/null 2>&1 || true
  git push origin "$tag" >/dev/null 2>&1 || true
  gh release create "$tag" "$apk_name" --repo "$REPO" --title "Kam AI $version" --notes "$notes"
fi

if $do_play; then
  echo "Uploading bundle to Play (internal track)..."
  python3 "$root/tools/play_publish.py" upload-bundle --aab "$aab"
fi

echo "Verifying the published APK..."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
gh release download "$tag" --repo "$REPO" --pattern "$apk_name" --dir "$tmp" --clobber
back_sha="$(sha256sum "$tmp/$apk_name" | cut -d' ' -f1)"
if [ "$sha" = "$back_sha" ]; then
  echo "Verified: published APK matches the built one ($sha)."
else
  echo "MISMATCH: built $sha but published $back_sha" >&2
  exit 1
fi

# If a phone is connected, install the downloaded APK and confirm it launches.
adb="$HOME/Android/Sdk/platform-tools/adb"
if [ -x "$adb" ] && [ -n "$($adb devices | sed -n '2p')" ]; then
  echo "Phone connected: install $tmp/$apk_name by hand to confirm it launches (kept out"
  echo "of this script so it never uninstalls a debug build with the owner's downloads)."
fi

rm -f "$apk_name"
echo "Done. GitHub: https://github.com/$REPO/releases/tag/$tag"
