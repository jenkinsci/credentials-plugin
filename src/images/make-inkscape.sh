#!/bin/sh -e

dir=$(dirname $0)
for src in "$dir"/*.svg
do
  echo "Processing $(basename "$src")..."
  file=$(basename "$src" | sed -e s/.svg/.png/ )
  for sz in 16 24 32 48
  do
    dst="${dir}/../../src/main/webapp/images/${sz}x${sz}/${file}"
    if [ ! -e "$dst" -o "$src" -nt "$dst" ]
    then
      echo -n "  generating ${sz}x${sz}..."
      mkdir "${dir}/../../src/main/webapp/images/${sz}x${sz}" > /dev/null 2>&1 || true
      inkscape -z -C -w ${sz} -h ${sz} -e "$dst" "$src" 2>&1 | grep "Bitmap saved as"
    fi
  done
done
