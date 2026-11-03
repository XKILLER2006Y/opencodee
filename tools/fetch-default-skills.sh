#!/usr/bin/env bash
# Replaces the condensed default skills in app/src/main/assets/default-skills with the upstream files.
# Run on a computer with internet access, review the diff, then commit.
#
#   tools/fetch-default-skills.sh                 # uses the default branch (main)
#   SLOP_REF=<commit> REA_REF=<commit> tools/fetch-default-skills.sh   # pin exact commits (recommended)
#
# The skills are always-on instructions for the agent, so review what you pull in (git diff) before committing.
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/default-skills"
SLOP_REF="${SLOP_REF:-main}"
REA_REF="${REA_REF:-main}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() { # owner/repo ref outdir
  mkdir -p "$3"
  curl -fL --progress-bar -o "$TMP/$(basename "$3").zip" "https://codeload.github.com/$1/zip/$2"
  unzip -q "$TMP/$(basename "$3").zip" -d "$3"
}

install_skill() { # srcdir name
  [ -f "$1/SKILL.md" ] || { echo "no SKILL.md in $1" >&2; exit 1; }
  rm -rf "$DEST/$2"
  mkdir -p "$DEST/$2"
  cp -R "$1"/. "$DEST/$2"/
  echo "installed $2 ($(find "$DEST/$2" -type f | wc -l) files)"
}

fetch jalaalrd/anti-ai-slop-writing "$SLOP_REF" "$TMP/slop"
SLOP_DIR="$(find "$TMP/slop" -type d -path '*/skills/anti-ai-slop-writing' | head -1)"
[ -n "$SLOP_DIR" ] || { echo "skills/anti-ai-slop-writing not found" >&2; exit 1; }
install_skill "$SLOP_DIR" anti-ai-slop-writing

fetch morluto/rea "$REA_REF" "$TMP/rea"
# the skill folder is the one whose SKILL.md declares name: reverse-engineer-anything
REA_MD="$(grep -rl --include=SKILL.md -E '^name:[[:space:]]*"?reverse-engineer-anything"?[[:space:]]*$' "$TMP/rea" | head -1 || true)"
[ -n "$REA_MD" ] || { echo "reverse-engineer-anything skill not found in morluto/rea" >&2; exit 1; }
install_skill "$(dirname "$REA_MD")" reverse-engineer-anything

echo "Done. Review with: git diff --stat app/src/main/assets/default-skills"
