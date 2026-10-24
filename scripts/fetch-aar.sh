#!/usr/bin/env bash
# Download the published LitecoinDevKit Android AAR into app/libs/.
set -euo pipefail

VERSION="${1:-3.1.0-litecoin.3}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/libs/bdk-ltc-android.aar"
URL="https://github.com/LitecoinDevKit/bdk-ffi/releases/download/${VERSION}/bdk-ltc-android.aar"

mkdir -p "$ROOT/app/libs"
echo "Fetching $URL"
curl -fL --retry 3 -o "$DEST" "$URL"
ls -lh "$DEST"
echo "OK → $DEST"
