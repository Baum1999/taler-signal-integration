#!/bin/bash
#
# This file is part of GNU Taler
# (C) 2024 Taler Systems S.A.
#
# GNU Taler is free software; you can redistribute it and/or modify it under the
# terms of the GNU General Public License as published by the Free Software
# Foundation; either version 3, or (at your option) any later version.
#
# GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
# A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
#

# To get the s2v command, do this:
#
# $ npm install svg2vectordrawable -g
#

ASSETS_DIR="../taler-assets/svg"
TMP_DIR="../taler-assets/s2v-tmp"
OUTPUT_DIR="./wallet/src/main/res/drawable/"

set -ex

mkdir -p "$TMP_DIR"

for d in "$ASSETS_DIR"/*; do
  s2v --folder="$d" --output="$TMP_DIR" --tint="?attr/colorControlNormal"
done

# remove unneeded icons
rm "$TMP_DIR/taler_logo_2021_plain.xml"
rm "$TMP_DIR/taler_logo_2022.xml"
rm "$TMP_DIR/logo_2021.xml"

# add tint
sed -i 's@android:viewportWidth@android:tint="?attr/colorControlNormal"\n    android:viewportWidth@g' "$TMP_DIR"/*.xml
# reduce size
sed -i 's@"1200dp"@"24dp"@g' "$TMP_DIR"/*.xml
# add path fillColor
sed -i 's@<path@<path\n        android:fillColor="#FF000000"@g' "$TMP_DIR"/*.xml

# move final files
mv "$TMP_DIR"/*.xml "$OUTPUT_DIR"

# remove tmp dir
rm -rf "$TMP_DIR"
