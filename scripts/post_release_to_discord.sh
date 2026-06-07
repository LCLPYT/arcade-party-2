#!/usr/bin/env bash
set -euo pipefail

# Post a GitHub release's changelog to a Discord webhook, once.
#
# One-off helper that mirrors the `discord` job in
# .github/workflows/release.yml. Use it to backfill a release that was
# published before the webhook existed. Future releases are posted
# automatically by the workflow.
#
# Usage:
#   DISCORD_WEBHOOK=<url> scripts/post_release_to_discord.sh [tag]
#   scripts/post_release_to_discord.sh <webhook_url> [tag]
#
#   tag       release tag to post (default: the latest published release)
#   DRY_RUN=1 print the payload instead of sending it

webhook="${DISCORD_WEBHOOK:-}"
tag=""

if [ -n "${1:-}" ]; then
  case "$1" in
    http://*|https://*) webhook="$1"; tag="${2:-}" ;;
    *)                  tag="$1" ;;
  esac
fi

if [ -z "$webhook" ]; then
  echo "error: no Discord webhook. Pass it as the first argument or set DISCORD_WEBHOOK." >&2
  exit 1
fi

# Derive owner/repo from the origin remote (supports git@ and https forms).
remote="$(git remote get-url origin)"
slug="$(printf '%s' "$remote" | sed -E 's#^git@github\.com:##; s#^https://github\.com/##; s#\.git$##')"

api="https://api.github.com/repos/${slug}/releases"
if [ -n "$tag" ]; then
  api="${api}/tags/${tag}"
else
  api="${api}/latest"
fi

release="$(curl -fsS -H "Accept: application/vnd.github+json" "$api")"
tag_name="$(printf '%s' "$release" | jq -r '.tag_name')"

# Build the embed. Truncation happens inside jq so it counts Unicode
# codepoints (not bytes) and can't split a multibyte char. Discord's embed
# description limit is 4096 chars; we cut at 3900 and link out for the rest.
# Title/color/link match the CI job for a consistent look.
payload="$(printf '%s' "$release" | jq \
  --arg slug "$slug" \
  '{
    embeds: [{
      title: ("🎉 " + $slug + " " + .tag_name),
      url: .html_url,
      description: (if (.body | length) > 3900
                    then (.body[0:3900] + "\n\n…[full changelog](" + .html_url + ")")
                    else .body end),
      color: 3066993
    }]
  }')"

if [ -n "${DRY_RUN:-}" ]; then
  printf '%s\n' "$payload"
  echo "DRY_RUN set: not posting." >&2
  exit 0
fi

curl -fsS -X POST "$webhook" \
  -H "Content-Type: application/json" \
  -d "$payload"

echo "Posted ${tag_name} changelog to Discord."
