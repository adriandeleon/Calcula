#!/usr/bin/env bash
#
# Regenerate every raster icon from the SVG masters in branding/.
#
# Two families come out of this:
#
#   calcula-app/src/main/resources/com/calcula/icons/calcula-<size>.png   the window icons
#   branding/calcula.{icns,png,ico}                                      the installer icons
#
# Run it after editing a master. It is not part of the build: rsvg-convert and iconutil are not
# present on a CI runner, and the outputs are committed precisely so the build never needs them.
#
# The masters SET the integral as text, so this needs a maths font installed — that font dependency
# is exactly what rasterising here removes from the shipped app.
#
# Usage: scripts/make-icons.sh
set -euo pipefail

cd "$(dirname "$0")/.."

MASTER=branding/calcula-icon.svg
SMALL=branding/calcula-icon-small.svg
ICONS=calcula-app/src/main/resources/com/calcula/icons

need() { command -v "$1" >/dev/null || { echo "need $1" >&2; exit 1; }; }
need rsvg-convert

# Window icons. Below 32 px the stack rules smudge together, so those sizes use the reduced mark.
for size in 512 256 128 64 48 32; do
    rsvg-convert -w "$size" -h "$size" "$MASTER" -o "$ICONS/calcula-$size.png"
done
for size in 24 16; do
    rsvg-convert -w "$size" -h "$size" "$SMALL" -o "$ICONS/calcula-$size.png"
done

# Linux installer icon.
rsvg-convert -w 512 -h 512 "$MASTER" -o branding/calcula.png

# macOS. iconutil demands this exact directory layout and these exact names.
if command -v iconutil >/dev/null; then
    set=$(mktemp -d)/calcula.iconset
    mkdir -p "$set"
    for size in 16 32 128 256 512; do
        src=$MASTER
        [ "$size" -lt 32 ] && src=$SMALL
        rsvg-convert -w "$size" -h "$size" "$src" -o "$set/icon_${size}x${size}.png"
        rsvg-convert -w "$((size * 2))" -h "$((size * 2))" "$MASTER" -o "$set/icon_${size}x${size}@2x.png"
    done
    iconutil -c icns "$set" -o branding/calcula.icns
    echo "wrote branding/calcula.icns"
else
    echo "no iconutil (not macOS): branding/calcula.icns left as-is" >&2
fi

# Windows. An .ico is a tiny directory of embedded PNGs, so it can be assembled without ImageMagick —
# which is not installed on this machine and would be one more thing to require of a contributor.
tmp=$(mktemp -d)
for size in 16 24 32 48 64 128 256; do
    src=$MASTER
    [ "$size" -lt 32 ] && src=$SMALL
    rsvg-convert -w "$size" -h "$size" "$src" -o "$tmp/$size.png"
done
java scripts/IcoWriter.java branding/calcula.ico "$tmp"/*.png

echo "icons regenerated"
