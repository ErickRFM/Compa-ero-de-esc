#!/usr/bin/env bash
# Test harness for check-whitespace.sh.
#
# Each case builds a throwaway repository, commits a known defect, and asserts
# both the range resolution and the verdict. These exist because the check this
# script replaces was inert: it passed unconditionally, which no amount of
# reading the YAML would have revealed.
#
# The fallback cases deliberately plant the defect in a commit that the obvious
# but wrong range would miss:
#
#   - a first commit with trailing whitespace, which `root...root` cannot see
#     because the diff is empty;
#   - a new branch whose *older* commit has the defect, which `HEAD~1...HEAD`
#     cannot see because it only looks at the last commit.
#
# A test that only asserts the fallback "succeeds" proves nothing. Each of
# those cases has to fail the scan to be worth having.
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-whitespace.sh"

WORK="${TMPDIR:-/tmp}/ws-test-$$"
mkdir -p "$WORK"
pass=0
fail=0

# Trailing whitespace must survive the commit, so line endings are left alone.
init_repo() {
  rm -rf "$WORK/repo"
  mkdir -p "$WORK/repo"
  git -C "$WORK/repo" init -q
  git -C "$WORK/repo" config user.email t@t.local
  git -C "$WORK/repo" config user.name T
  git -C "$WORK/repo" config core.autocrlf false
}

# A repository whose first commit is "base", plus a clean second commit.
setup() {
  init_repo
  echo "clean" > "$WORK/repo/a.md"
  echo "clean" > "$WORK/repo/b.md"
  git -C "$WORK/repo" add -A
  git -C "$WORK/repo" commit -q -m base
  mkdir -p "$WORK/repo/infrastructure/scripts"
  cp "$SCRIPT" "$WORK/repo/check.sh"
}

# A repository with exactly one commit, so the "no previous commit" path runs.
setup_root() {
  init_repo
  printf 'clean\n' > "$WORK/repo/a.md"
  git -C "$WORK/repo" add -A
  git -C "$WORK/repo" commit -q -m root
  mkdir -p "$WORK/repo/infrastructure/scripts"
  cp "$SCRIPT" "$WORK/repo/check.sh"
}

commit() { git -C "$WORK/repo" add -A && git -C "$WORK/repo" commit -q -m "$1"; }

# run <before-sha-or-empty> <event> [base-ref]
run() {
  local before="$1" event="$2" base="${3:-}"
  ( cd "$WORK/repo" \
    && GITHUB_EVENT_NAME="$event" GITHUB_EVENT_BEFORE="$before" GITHUB_BASE_REF="$base" \
       bash check.sh 2>&1 )
}

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  PASS  $name"
    pass=$((pass + 1))
  else
    echo "  FAIL  $name (expected $expected, got $actual)"
    fail=$((fail + 1))
  fi
}

check_contains() {
  local name="$1" haystack="$2" needle="$3"
  if printf '%s' "$haystack" | grep -q "$needle"; then
    echo "  PASS  $name"
    pass=$((pass + 1))
  else
    echo "  FAIL  $name (missing: $needle)"
    fail=$((fail + 1))
  fi
}

head1() { git -C "$WORK/repo" rev-parse HEAD~1 2>/dev/null || echo ""; }

echo "CASE 1: trailing whitespace in a committed change is caught"
setup
printf 'clean   \n' > "$WORK/repo/a.md"
commit bad
out=$(run "$(head1)" push); code=$?
check "exit code is non-zero" "1" "$code"
check_contains "names the defect" "$out" "trailing whitespace"

echo "CASE 2: the replaced command would have MISSED the same defect"
setup
printf 'clean   \n' > "$WORK/repo/a.md"
commit bad
( cd "$WORK/repo" && git diff --check >/dev/null 2>&1 )
check "bare git diff --check passes anyway" "0" "$?"

echo "CASE 3: a clean change passes"
setup
printf 'cleaner\n' > "$WORK/repo/a.md"
commit good
out=$(run "$(head1)" push); code=$?
check "exit code is zero" "0" "$code"
check_contains "reports clean" "$out" "no whitespace errors"

echo "CASE 4: blank line at EOF is caught"
setup
printf 'x\n\n\n' > "$WORK/repo/a.md"
commit eof
run "$(head1)" push >/dev/null
check "exit code is non-zero" "1" "$?"

echo "CASE 5: first commit, clean, passes and announces the fallback"
setup_root
out=$(run 0000000000000000000000000000000000000000 push); code=$?
check "exit code is zero" "0" "$code"
check_contains "announces the fallback" "$out" "entire repository history"

echo "CASE 6: a defect IN THE FIRST COMMIT is caught"
# The old fallback resolved this to `root...root`, an empty diff, and reported
# PASS while claiming to check the whole repository.
setup_root
printf 'clean   \n' > "$WORK/repo/a.md"
commit root2
out=$(run 0000000000000000000000000000000000000000 push); code=$?
check "exit code is non-zero" "1" "$code"
check_contains "names the defect" "$out" "trailing whitespace"

echo "CASE 7: push to a new branch, clean, succeeds and announces"
setup
out=$(run deadbeefdeadbeefdeadbeefdeadbeefdeadbeef push); code=$?
check "exit code is zero" "0" "$code"
check_contains "announces the fallback" "$out" "new branch"

echo "CASE 8: a defect introduced earlier in a new branch is still caught"
# The old fallback used `HEAD~1...HEAD`, which only ever saw the newest commit.
# Here the defect is in a.md and the tip commit only touches b.md, so the last
# commit's diff looks clean while the branch is not.
setup
printf 'clean   \n' > "$WORK/repo/a.md"
commit older_bad
printf 'cleaner\n' > "$WORK/repo/b.md"
commit newer_unrelated
git -C "$WORK/repo" show HEAD:a.md | grep -q 'clean   ' || { echo "  setup error: defect not present at the tip"; exit 1; }
out=$(run deadbeefdeadbeefdeadbeefdeadbeefdeadbeef push); code=$?
check "exit code is non-zero" "1" "$code"
check_contains "names the defect" "$out" "trailing whitespace"

echo "CASE 8b: a defect fixed in a later commit does NOT block the tip"
# `git diff --check` inspects the diff, not every commit's diff. If the tip is
# clean the check passes, which is the correct and intended outcome: the branch
# does not deliver the defect. Stated explicitly so the behaviour is a decision
# rather than a surprise.
setup
printf 'clean   \n' > "$WORK/repo/a.md"
commit broken
printf 'clean\n' > "$WORK/repo/a.md"
commit fixed
out=$(run deadbeefdeadbeefdeadbeefdeadbeefdeadbeef push); code=$?
check "exit code is zero" "0" "$code"

echo "CASE 9: a new branch compares against the default branch when it exists"
setup
git -C "$WORK/repo" update-ref refs/remotes/origin/main HEAD
printf 'clean   \n' > "$WORK/repo/a.md"
commit branch_bad
out=$(run deadbeefdeadbeefdeadbeefdeadbeefdeadbeef push); code=$?
check "exit code is non-zero" "1" "$code"

echo "CASE 10: pull_request resolves the three-dot base range"
setup
# What actions/checkout with fetch-depth: 0 leaves behind on a pull_request.
git -C "$WORK/repo" update-ref refs/remotes/origin/base HEAD
printf 'clean2\n' > "$WORK/repo/a.md"
commit pr
out=$(run "" pull_request base); code=$?
check "exit code is zero" "0" "$code"
check_contains "uses the three-dot range" "$out" "origin/base...HEAD"

echo "CASE 11: genuinely binary files are skipped"
# git diff --check ignores content it detects as binary. The point is the
# binary detection, not an extension allowlist, so the file is named .txt on
# purpose: a text file called .bin with trailing whitespace IS a defect.
setup
mkdir -p "$WORK/repo/imgs"
printf 'PNG\001\002\000\000trailing   \n' > "$WORK/repo/imgs/x.txt"
commit binary
run "$(head1)" push >/dev/null
check "binary content is not flagged" "0" "$?"

echo "CASE 12: a text file with a non-source extension IS flagged"
# The old path filter would have skipped this silently.
setup
mkdir -p "$WORK/repo/sql"
printf 'select 1;   \n' > "$WORK/repo/sql/q.sql"
commit sqldefect
run "$(head1)" push >/dev/null
check "exit code is non-zero" "1" "$?"

echo "CASE 13: a pull_request whose base ref is missing falls back loudly"
setup
printf 'clean3\n' > "$WORK/repo/a.md"
commit pr
out=$(run "" pull_request nonexistent_base); code=$?
check "still succeeds" "0" "$code"
check_contains "emits a warning annotation" "$out" "::warning::"

rm -rf "$WORK"
echo ""
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
