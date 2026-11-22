#!/usr/bin/env bash
# Renders the NSIS installer images from the HTML sources in this folder:
#   sidebar.bmp  (welcome/finish pages, 164×314 at 1.5x = 246×471)
#   header.bmp   (inner pages,          150×57  at 1.5x = 225×86)
# NSIS stretches the bitmaps to the control size, which scales with Windows display
# scaling; 1.5x is exact at 150 % and the large shapes survive 100 %/200 %.
#
# Needs a Chromium-based browser (BROWSER=…; defaults to chromium/google-chrome, or
# Microsoft Edge when running in WSL) and ImageMagick. Output is committed.
set -euo pipefail
cd "$(dirname "$0")"
REPO=$(cd ../.. && pwd)
SCALE=1.5

browser=${BROWSER:-}
if [[ -z $browser ]]; then
  for candidate in chromium chromium-browser google-chrome \
    "/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"; do
    if command -v "$candidate" >/dev/null 2>&1 || [[ -x $candidate ]]; then
      browser=$candidate
      break
    fi
  done
fi
[[ -n $browser ]] || { echo "No Chromium-based browser found (set BROWSER)" >&2; exit 1; }

# A Windows browser can't read WSL paths reliably, so stage the files on the Windows side.
if [[ $browser == *.exe ]]; then
  work=$(wslpath "$(cmd.exe /c 'echo %TEMP%' 2>/dev/null | tr -d '\r')")/nod-installer-render
else
  work=$(mktemp -d)
fi
rm -rf "$work" && mkdir -p "$work"
trap 'rm -rf "$work"' EXIT

cp sidebar.html header.html "$work/"
cp "$REPO/design/mark.svg" "$work/mark.svg"
sed 's/currentColor/#ffffff/g' "$REPO/design/mark-mono.svg" >"$work/mark-white.svg"
cp "$REPO/node_modules/@fontsource-variable/geist/files/geist-latin-wght-normal.woff2" "$work/geist.woff2"

url_of() {
  if [[ $browser == *.exe ]]; then
    echo "file:///$(wslpath -w "$work/$1" | tr '\\' '/')"
  else
    echo "file://$work/$1"
  fi
}
out_of() {
  if [[ $browser == *.exe ]]; then wslpath -w "$work/$1"; else echo "$work/$1"; fi
}

render() { # name width height
  "$browser" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=$SCALE --window-size="$2,$3" \
    --virtual-time-budget=2000 --screenshot="$(out_of "$1.png")" "$(url_of "$1.html")" \
    >/dev/null 2>&1
  # NSIS wants an uncompressed 24-bit BMP without alpha.
  convert "$work/$1.png" -alpha off -type TrueColor "BMP3:$1.bmp"
  echo "$1.bmp: $(identify -format '%wx%h %[bit-depth]-bit' "$1.bmp")"
}

render sidebar 164 314
render header 150 57
