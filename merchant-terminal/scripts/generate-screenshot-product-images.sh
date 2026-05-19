#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSET_DIR="$SCRIPT_DIR/../src/debug/assets/screenshot-products"
FONT_PATH="/System/Library/Fonts/Supplemental/Arial Bold.ttf"
mkdir -p "$ASSET_DIR"

magick -size 640x640 xc:'#f3e8d2' \
  -fill '#6b3f2c' -draw 'roundrectangle 170,180 470,500 42,42' \
  -fill '#f8f4ec' -draw 'ellipse 320,250 88,44 0,360' \
  -fill '#dcc7ae' -draw 'ellipse 320,250 70,30 0,360' \
  -fill '#6b3f2c' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Coffee' \
  "$ASSET_DIR/coffee.png"

magick -size 640x640 xc:'#dff1e2' \
  -fill '#72a96c' -draw 'polygon 320,150 430,350 210,350' \
  -fill '#c7e0a8' -draw 'polygon 320,210 390,330 250,330' \
  -fill '#3c6b48' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Tea' \
  "$ASSET_DIR/tea.png"

magick -size 640x640 xc:'#f6e7d7' \
  -fill '#c8843b' -draw 'path \"M 150,360 Q 320,120 490,360 Q 320,470 150,360 z\"' \
  -fill '#e3ad62' -draw 'path \"M 210,335 Q 320,190 430,335 Q 320,405 210,335 z\"' \
  -fill '#80511f' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Croissant' \
  "$ASSET_DIR/croissant.png"

magick -size 640x640 xc:'#efe7dc' \
  -fill '#c9914e' -draw 'roundrectangle 140,210 500,450 36,36' \
  -fill '#7ebc6f' -draw 'roundrectangle 165,250 475,300 25,25' \
  -fill '#d94b46' -draw 'roundrectangle 165,305 475,340 20,20' \
  -fill '#f4deb2' -draw 'roundrectangle 165,345 475,410 24,24' \
  -fill '#70491e' -gravity south -font "$FONT_PATH" -pointsize 50 -annotate +0+48 'Sandwich' \
  "$ASSET_DIR/sandwich.png"
