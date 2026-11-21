#!/usr/bin/env bash
# Renders the README images from raw screenshots with a headless Chromium browser.
# Usage: scripts/render-mockups.sh <raw-dir>
#   <raw-dir> must contain: android-lockscreen.png, android-inbox.png, android-detail.png,
#                           desktop-island-request.png, desktop-main-light-tall.png
# Output: docs/images/{phone-lockscreen,phone-app,desktop}.png
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
RAW=$(cd "${1:?raw screenshot directory}" && pwd)
OUT="$ROOT/docs/images"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

cp "$ROOT"/design/mockups/*.html "$ROOT/design/mark.svg" "$ROOT/design/logo.svg" "$RAW"/*.png "$WORK/"
FONTS="$ROOT/node_modules/@fontsource-variable"
cp "$FONTS/geist/files/geist-latin-wght-normal.woff2" "$WORK/geist.woff2"
cp "$FONTS/geist-mono/files/geist-mono-latin-wght-normal.woff2" "$WORK/geist-mono.woff2"
cp "$ROOT/desktop/src/assets/fonts/GeistPixel-Square.woff2" "$WORK/geist-pixel.woff2"

# Chromium on Linux/macOS, or Edge on Windows/WSL.
BROWSER=${BROWSER:-$(command -v chromium || command -v google-chrome || command -v chrome || true)}
EDGE="/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
if [[ -z "$BROWSER" && -x "$EDGE" ]]; then
  # Edge (a Windows program) can't read WSL paths reliably: work in a Windows temp folder.
  WIN_TMP="/mnt/c/Users/$(cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r')/AppData/Local/Temp/nod-mockups"
  rm -rf "$WIN_TMP" && mkdir -p "$WIN_TMP" && cp "$WORK"/* "$WIN_TMP/"
  WORK_URL="file:///$(wslpath -m "$WIN_TMP")"
  shot() { (cd /mnt/c && "$EDGE" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --default-background-color=00000000 \
    --window-size="$1" --screenshot="$(wslpath -w "$WIN_TMP/$2")" "$WORK_URL/$3" >/dev/null 2>&1); cp "$WIN_TMP/$2" "$OUT/$2"; }
else
  [[ -n "$BROWSER" ]] || { echo "No Chromium/Chrome/Edge found (set BROWSER=…)"; exit 1; }
  WORK_URL="file://$WORK"
  shot() { "$BROWSER" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --default-background-color=00000000 \
    --window-size="$1" --screenshot="$WORK/$2" "$WORK_URL/$3" >/dev/null 2>&1; cp "$WORK/$2" "$OUT/$2"; }
fi

mkdir -p "$OUT"
have() { for f in "$@"; do [[ -f "$RAW/$f" ]] || { echo "· skipped (missing $f)"; return 1; }; done; }
have android-lockscreen.png && shot 1400,1240 phone-lockscreen.png "phone.html?shots=android-lockscreen.png"
have android-inbox.png android-detail.png &&
  shot 1400,1240 phone-app.png "phone.html?shots=android-inbox.png,android-detail.png"
have desktop-island-request.png desktop-main-light-tall.png &&
  shot 1800,1300 desktop.png "desktop.html?island=desktop-island-request.png&window=desktop-main-light-tall.png&windowWidth=760"
# Social preview reuses the rendered desktop image (upload it under Settings › Social preview).
if [[ -f "$OUT/desktop.png" ]]; then
  cp "$OUT/desktop.png" "$WORK/desktop.png"; [[ -n "${WIN_TMP:-}" ]] && cp "$OUT/desktop.png" "$WIN_TMP/desktop.png"
  shot 1280,640 social-preview.png "social.html"
fi

# Trim the transparent margin (keeping room for the soft shadow).
for f in phone-lockscreen.png phone-app.png desktop.png; do
  [[ -f "$OUT/$f" ]] && command -v convert >/dev/null &&
    convert "$OUT/$f" -trim +repage -bordercolor none -border 24 -strip "$OUT/$f"
done
echo "✓ Rendered into $OUT"
