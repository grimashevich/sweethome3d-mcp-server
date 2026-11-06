#!/usr/bin/env bash
# setup-dev.sh — Obtain SweetHome3D.jar required for building the plugin.
#
# Tries in order:
#   1. Already exists in lib/ → nothing to do
#   2. Copies from an installed Sweet Home 3D (fastest, works offline)
#   3. Downloads from SourceForge (requires internet)
#
# Usage:
#   ./scripts/setup-dev.sh

set -e

DEST="lib/SweetHome3D.jar"
SH3D_VERSION="7.5"
SOURCEFORGE_URL="https://unlimited.dl.sourceforge.net/project/sweethome3d/SweetHome3D/SweetHome3D-${SH3D_VERSION}/SweetHome3D-${SH3D_VERSION}.jar"

# Known installation paths (Windows Git Bash, macOS, Linux)
INSTALL_PATHS=(
  "/c/Program Files/Sweet Home 3D/SweetHome3D.jar"
  "/c/Program Files (x86)/Sweet Home 3D/SweetHome3D.jar"
  "$HOME/Applications/Sweet Home 3D.app/Contents/app/SweetHome3D.jar"
  "/Applications/Sweet Home 3D.app/Contents/app/SweetHome3D.jar"
  "$HOME/Applications/Sweet Home 3D.app/Contents/Resources/Java/SweetHome3D.jar"
  "/Applications/Sweet Home 3D.app/Contents/Resources/Java/SweetHome3D.jar"
  "/usr/share/sweethome3d/SweetHome3D.jar"
  "/opt/sweethome3d/SweetHome3D.jar"
)

# lib/jmf.jar (the movie writer used by export_video): nested in the SourceForge jar,
# next to SweetHome3D.jar in an installed Sweet Home 3D.
get_jmf() {
  if [ -f lib/jmf.jar ]; then
    return
  fi
  if unzip -l "$DEST" jmf.jar >/dev/null 2>&1; then
    unzip -o -j -q "$DEST" jmf.jar -d lib
  else
    for path in "${INSTALL_PATHS[@]}"; do
      if [ -f "$(dirname "$path")/jmf.jar" ]; then
        cp "$(dirname "$path")/jmf.jar" lib/jmf.jar
        break
      fi
    done
  fi
  if [ ! -f lib/jmf.jar ]; then
    echo "✗ jmf.jar not found: copy it from your Sweet Home 3D installation to lib/jmf.jar" >&2
    exit 1
  fi
  echo "✓ lib/jmf.jar ready"
}

# 1. Already present
if [ -f "$DEST" ]; then
  echo "✓ $DEST already exists."
  get_jmf
  exit 0
fi

mkdir -p lib

# 2. Copy from an installed Sweet Home 3D
for path in "${INSTALL_PATHS[@]}"; do
  if [ -f "$path" ]; then
    echo "Found Sweet Home 3D at: $path"
    cp "$path" "$DEST"
    echo "✓ Copied to $DEST"
    get_jmf
    exit 0
  fi
done

# 3. Download from SourceForge
echo "Sweet Home 3D installation not found. Downloading SweetHome3D-${SH3D_VERSION}.jar from SourceForge..."
echo "URL: $SOURCEFORGE_URL"
curl -L --progress-bar "$SOURCEFORGE_URL" -o "$DEST"
echo "✓ Downloaded to $DEST"
get_jmf
