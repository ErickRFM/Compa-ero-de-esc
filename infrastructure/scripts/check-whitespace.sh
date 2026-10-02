#!/usr/bin/env bash
# Checks whitespace errors in the changes a build is actually responsible for.
#
# Why this exists instead of a bare `git diff --check`:
#
# `git diff --check` with no range compares the working tree against the index.
# On a CI checkout that comparison is always empty, so the command exits 0 no
# matter what defects the branch actually contains. It looked like a check and
# could never fail. This was confirmed empirically: a commit with trailing
# whitespace and a blank line at EOF passed `git diff --check` and was caught
# only once the range was specified.
#
# The range is resolved from the event that triggered the workflow:
#
#   pull_request  origin/${{ github.base_ref }}...HEAD  (three-dot: only this
#                 branch's commits, ignoring unrelated base-branch movement)
#   push          ${{ github.event.before }}...HEAD
#
# A push that creates a branch has a before SHA of all zeros, and that SHA is
# not in the local history either. There is no range to diff against, so the
# fallback is the merge base with the default branch: everything the branch has
# that main does not. If there is no default branch to compare against, the
# empty tree is used and the whole repository is checked.
#
# The previous version resolved that case to `HEAD~1`, which checked only the
# last commit of a branch that might have ten. It also resolved "no previous
# commit" to `root...root`, an empty diff that inspected nothing while
# announcing that it was checking the entire repository.
#
# Every fallback is announced rather than applied silently, because a check
# that quietly inspects nothing is the same defect this file exists to fix.
set -euo pipefail

empty_tree=$(git hash-object -t tree /dev/null)
default_branch="${GITHUB_EVENT_REPOSITORY_DEFAULT_BRANCH:-main}"

# The revision arguments are carried in an array because the two cases are not
# expressible as one string:
#
#   commit mode  git diff "$SHA...HEAD"   <- the three dots must be attached to
#                                            the SHA, not passed as a second
#                                            argument. `git diff A ...B` is
#                                            rejected as an unknown revision.
#   tree mode    git diff "$TREE" HEAD    <- the only valid form, since a tree
#                                            cannot be a symmetric-difference
#                                            endpoint
#
# Three ways this went wrong first, and all three reported PASS:
#
#   1. Writing the empty-tree case as "$empty_tree...HEAD" gives rc=128
#      "is a tree, not a commit". Because the file listing below used to end in
#      `|| true`, that fatal error was swallowed, the file list came back
#      empty, and the script reported success.
#   2. Passing the range as a single quoted string, "$LEFT $REST", made git
#      receive one argument containing a space, which is not a revision.
#   3. Passing "$LEFT" "...HEAD" as two arguments is the near-miss above.
#
# A check that turns its own crash into a green result is the same defect as
# the bare `git diff --check` this file exists to replace.
DIFF_ARGS=()
RANGE_DISPLAY=""

use_range() { DIFF_ARGS=("$1...HEAD"); RANGE_DISPLAY="$1...HEAD"; }
use_tree()   { DIFF_ARGS=("$1" "HEAD");  RANGE_DISPLAY="<empty tree> HEAD"; }

merge_base_range() {
  # Everything HEAD has that the default branch does not.
  local base="origin/$default_branch"
  if git rev-parse --verify --quiet "$base" >/dev/null; then
    if base_sha=$(git merge-base "$base" HEAD 2>/dev/null); then
      use_range "$base_sha"
      return 0
    fi
  fi
  echo "::warning::No merge base against origin/$default_branch; checking the whole tree"
  use_tree "$empty_tree"
}

if [ "${GITHUB_EVENT_NAME:-}" = "pull_request" ]; then
  base="origin/${GITHUB_BASE_REF:-}"
  if [ -n "${GITHUB_BASE_REF:-}" ] && git rev-parse --verify --quiet "$base" >/dev/null; then
    use_range "$base"
  else
    echo "::warning::Base ref $base not available; falling back to the default branch"
    merge_base_range
  fi
elif [ -n "${GITHUB_EVENT_BEFORE:-}" ] && [ "$GITHUB_EVENT_BEFORE" != "0000000000000000000000000000000000000000" ]; then
  if git rev-parse --verify --quiet "${GITHUB_EVENT_BEFORE}^{commit}" >/dev/null; then
    use_range "$GITHUB_EVENT_BEFORE"
  else
    echo "::warning::Before SHA ${GITHUB_EVENT_BEFORE} is not in history (new branch); checking every commit the branch adds"
    merge_base_range
  fi
else
  echo "::notice::No previous commit found; checking the entire repository history"
  merge_base_range
fi

echo "whitespace range: $RANGE_DISPLAY"

# No path filter. `git diff --check` already skips binary content, and the
# filter that used to restrict this to a list of source extensions silently
# excluded the shell scripts in infrastructure/scripts, including this file and
# the secret scan it runs alongside.
#
# A non-zero exit here is a failure to resolve the range, not "no changes".
# It must never be treated as success.
if ! changed=$(git diff --name-only "${DIFF_ARGS[@]}"); then
  echo "::error::git diff could not resolve '${DIFF_ARGS[*]}'; refusing to report success"
  exit 1
fi
if [ -z "$changed" ]; then
  echo "no files changed in this range"
  exit 0
fi

echo "files in range:"
echo "$changed" | sed 's/^/  /'

# --check reports on added lines only, so pre-existing whitespace elsewhere in
# the repository does not block an unrelated change.
if git diff --check "${DIFF_ARGS[@]}"; then
  echo "no whitespace errors"
else
  echo "::error::Whitespace errors in committed changes"
  exit 1
fi
