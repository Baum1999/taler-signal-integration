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

magick -size 640x640 xc:'#fff4d8' \
  -fill '#f2c84b' -draw 'roundrectangle 220,150 420,455 38,38' \
  -fill '#fff9df' -draw 'rectangle 245,190 395,230' \
  -fill '#df8f2d' -draw 'circle 320,300 370,300' \
  -fill '#815c13' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Juice' \
  "$ASSET_DIR/juice.png"

magick -size 640x640 xc:'#ead8c8' \
  -fill '#ffffff' -draw 'roundrectangle 230,180 410,430 22,22' \
  -fill '#3b2118' -draw 'ellipse 320,250 72,42 0,360' \
  -fill '#6b3f2c' -draw 'ellipse 320,255 54,28 0,360' \
  -fill '#5a3224' -gravity south -font "$FONT_PATH" -pointsize 50 -annotate +0+48 'Espresso' \
  "$ASSET_DIR/espresso.png"

magick -size 640x640 xc:'#fff1c9' \
  -fill '#f5d34f' -draw 'roundrectangle 210,165 430,455 44,44' \
  -fill '#fff8d8' -draw 'rectangle 240,210 400,250' \
  -fill '#77a556' -draw 'line 360,150 320,210' \
  -fill '#7a6415' -gravity south -font "$FONT_PATH" -pointsize 48 -annotate +0+48 'Lemonade' \
  "$ASSET_DIR/lemonade.png"

magick -size 640x640 xc:'#f6e7d7' \
  -fill '#c8843b' -draw 'path \"M 150,360 Q 320,120 490,360 Q 320,470 150,360 z\"' \
  -fill '#e3ad62' -draw 'path \"M 210,335 Q 320,190 430,335 Q 320,405 210,335 z\"' \
  -fill '#80511f' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Croissant' \
  "$ASSET_DIR/croissant.png"

magick -size 640x640 xc:'#efe2f2' \
  -fill '#c18a43' -draw 'circle 320,305 455,305' \
  -fill '#e5bd77' -draw 'circle 320,285 420,285' \
  -fill '#4a65a4' -draw 'circle 270,250 288,250' \
  -fill '#4a65a4' -draw 'circle 350,275 370,275' \
  -fill '#6a3b79' -gravity south -font "$FONT_PATH" -pointsize 52 -annotate +0+48 'Muffin' \
  "$ASSET_DIR/muffin.png"

magick -size 640x640 xc:'#f1e0c9' \
  -fill '#b87938' -draw 'ellipse 320,300 170,120 0,360' \
  -fill '#f5d7a6' -draw 'ellipse 320,300 80,55 0,360' \
  -fill '#f9f0df' -draw 'ellipse 320,300 52,34 0,360' \
  -fill '#73502a' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Bagel' \
  "$ASSET_DIR/bagel.png"

magick -size 640x640 xc:'#efe7dc' \
  -fill '#c9914e' -draw 'roundrectangle 140,210 500,450 36,36' \
  -fill '#7ebc6f' -draw 'roundrectangle 165,250 475,300 25,25' \
  -fill '#d94b46' -draw 'roundrectangle 165,305 475,340 20,20' \
  -fill '#f4deb2' -draw 'roundrectangle 165,345 475,410 24,24' \
  -fill '#70491e' -gravity south -font "$FONT_PATH" -pointsize 50 -annotate +0+48 'Sandwich' \
  "$ASSET_DIR/sandwich.png"

magick -size 640x640 xc:'#eee5d7' \
  -fill '#d6b070' -draw 'polygon 230,170 440,250 360,455 170,380' \
  -fill '#87b96d' -draw 'polygon 255,220 400,275 345,390 210,345' \
  -fill '#f7e3b7' -draw 'polygon 275,260 380,295 335,360 235,330' \
  -fill '#6d4a21' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Wrap' \
  "$ASSET_DIR/wrap.png"

magick -size 640x640 xc:'#e8f4e2' \
  -fill '#78aa55' -draw 'ellipse 320,325 180,110 0,360' \
  -fill '#b6d26d' -draw 'circle 255,290 282,290' \
  -fill '#d86f4a' -draw 'circle 355,310 380,310' \
  -fill '#f0d487' -draw 'circle 315,350 340,350' \
  -fill '#456d2e' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Salad' \
  "$ASSET_DIR/salad.png"

magick -size 640x640 xc:'#f5e4d4' \
  -fill '#e38a34' -draw 'ellipse 320,320 180,95 0,360' \
  -fill '#f5b05a' -draw 'ellipse 320,300 140,65 0,360' \
  -fill '#f9e4b0' -draw 'circle 270,285 285,285' \
  -fill '#8a4a1c' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Soup' \
  "$ASSET_DIR/soup.png"

magick -size 640x640 xc:'#f5ead8' \
  -fill '#c88a3d' -draw 'path \"M 190,390 A 150,150 0 0 1 450,240 L 450,390 z\"' \
  -fill '#f0d091' -draw 'path \"M 240,365 A 110,110 0 0 1 420,260 L 420,365 z\"' \
  -fill '#75a65d' -draw 'circle 335,310 352,310' \
  -fill '#82521f' -gravity south -font "$FONT_PATH" -pointsize 52 -annotate +0+48 'Quiche' \
  "$ASSET_DIR/quiche.png"

magick -size 640x640 xc:'#edf2f6' \
  -fill '#ffffff' -draw 'roundrectangle 210,170 430,450 36,36' \
  -fill '#d7b36a' -draw 'rectangle 235,285 405,405' \
  -fill '#c74e58' -draw 'circle 280,250 302,250' \
  -fill '#4b6fae' -draw 'circle 345,240 365,240' \
  -fill '#49535f' -gravity south -font "$FONT_PATH" -pointsize 50 -annotate +0+48 'Granola' \
  "$ASSET_DIR/granola.png"

magick -size 640x640 xc:'#edf0d8' \
  -fill '#cda94e' -draw 'polygon 210,210 370,250 300,430' \
  -fill '#dfbf66' -draw 'polygon 315,190 460,285 345,430' \
  -fill '#8d7425' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Chips' \
  "$ASSET_DIR/chips.png"

magick -size 640x640 xc:'#f1edf6' \
  -fill '#ffffff' -draw 'roundrectangle 220,170 420,450 36,36' \
  -fill '#f0c158' -draw 'circle 285,275 315,275' \
  -fill '#d95b5b' -draw 'circle 350,275 378,275' \
  -fill '#79a85f' -draw 'circle 320,340 350,340' \
  -fill '#5c5570' -gravity south -font "$FONT_PATH" -pointsize 54 -annotate +0+48 'Fruit' \
  "$ASSET_DIR/fruit.png"
