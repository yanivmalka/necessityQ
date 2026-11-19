#!/usr/bin/env bash
# Deletes the 27 already-merged branches of yanivmalka/necessityQ from GitHub.
#
# Each branch is deleted only if it still points at the commit checked on 2026-11-19
# (all of them are in merged PRs #1-#27). If someone pushed to a branch since then,
# that branch is skipped and left in place.
#
# Run from inside a clone of the repository (Git Bash on Windows works):
#   bash delete-merged-branches.sh
#
# To restore a branch later:  git push origin <commit>:refs/heads/<branch>
# (the commit of each branch is listed below)

set -u

BRANCHES="
ccr-119b64ad-npdfug 4bcd39c82c2fba7c214e9a0f97933624a78eaeb0
ccr-318377b1-unn4ml 743ece38de0655f1d40298bca6eff26e0d6841e4
ccr-7dbaf149-rcyi9f c765098b136ba6509f12c60a0420d326a0633678
claude/design-system-extraction-5i96fa ae987d608ce2212af6d11354f9fc336ed2f120dc
claude/firestore-rules-hardening a94a93740253f923e285b20590ba08ba7b8160fd
claude/funny-bell-ofj02c 9964ef627d5272c463f0b03ad3be0fef034b65fc
claude/funny-volta-o6u31r 6c487481c5f2644b01e42cd7fc48adcd74dc2db3
claude/list-screen-declutter 74a3fbb8242c69fdccad68e1775fc6b0510a3f04
claude/practical-darwin-snoddf 205995d45da8fd22d7056cc2293828526fbcef7f
claude/quirky-knuth-95ves8 c9521e766666194e851e2ff4581beb4d2cda535e
claude/upbeat-galileo-4xjl0c defe50071eab56cefa79ca2e7303005f60035017
claude/vibrant-heisenberg-lg1bq7 e4a5c7ba8d6db42a532283c62ebdfdfbf181ae08
claude/vigilant-mayer-4e4qjm 23e32d3d4577a27fbca87a06f41e12e39070fc0a
claude/wizardly-ritchie-4bp689 44570de2a1054954ca80a69a9ca47bafe7c8664f
feat/chat-edit-delete-retention 7008309e41f7cb8fb3938aecac945c5bc80a5fa9
feat/filter-chat-swipe-details dd768a3f66b80021102709e5c0f40a41670a187c
feature/barcode-location-units d51ba0efed9aec7fa83c56173cfd732948765a6c
feature/budget-guard c3235e4847b0d94e48dfe4f582927ba38e65a647
feature/item-price-range 93a31c4c3c784d9dfa30179422a7f73ca606a7fb
feature/menu-recipes-family 4b186d62c3d8d2ef915cc5086630babf325b24b2
feature/price-ingestion cff12447eb791ef1986e6494526f66b539700090
feature/tzameret-nutrition 30bc265411e7ff3f427291c1197cb3d0d4a5c8b6
fix/price-branches-retry d9c2bbaa7265b6ceb99c41a626567be2ccd850c4
fix/produce-matching cb8fe10bde25806a7e7a211d0bbd5829b5d4588a
fix/produce-weight-recipe-merge 984a67fe1feb45cdfb1e60cc5a290999e7a8b21b
list-tools-chat-media ff722eca51b47cdd4c498e7ad338d16567a13eea
ui/suggestion-strips 9878413e2b24400218c27c7c24c3241061b8bb9b
"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Run this from inside a clone of necessityQ." >&2
  exit 1
fi
if ! git remote get-url origin 2>/dev/null | grep -qi "yanivmalka/necessityQ"; then
  echo "The 'origin' remote is not yanivmalka/necessityQ:" >&2
  git remote get-url origin >&2
  exit 1
fi

echo "Branches to delete from GitHub:"
echo "$BRANCHES" | awk 'NF{print "  " $1}'
echo
read -r -p "Delete these $(echo "$BRANCHES" | awk 'NF' | wc -l | tr -d ' ') branches? (y/N) " answer
case "$answer" in
  y|Y|yes|YES) ;;
  *) echo "Nothing deleted."; exit 0 ;;
esac

deleted=0; skipped=0; failed=0
while read -r branch commit; do
  [ -z "${branch:-}" ] && continue
  current=$(git ls-remote --heads origin "refs/heads/$branch" | cut -f1)
  if [ -z "$current" ]; then
    echo "-  $branch: already gone"
    continue
  fi
  if [ "$current" != "$commit" ]; then
    echo "!  $branch: has new commits since it was checked; left in place"
    skipped=$((skipped + 1))
    continue
  fi
  if git push --quiet origin --force-with-lease="refs/heads/$branch:$commit" --delete "$branch"; then
    echo "✓  $branch"
    deleted=$((deleted + 1))
  else
    echo "✗  $branch: delete failed"
    failed=$((failed + 1))
  fi
done <<< "$BRANCHES"

echo
echo "Deleted: $deleted   Left in place: $skipped   Failed: $failed"
git fetch --prune --quiet origin 2>/dev/null || true
