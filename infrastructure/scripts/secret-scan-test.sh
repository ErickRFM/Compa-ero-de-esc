#!/usr/bin/env bash
# Proves secret-scan.sh fails on a planted credential, and passes once removed.
# A control that has only ever printed PASS has not been tested.
#
# Every fixture below is assembled at runtime from fragments, never written as a
# literal. That is not stylistic: this file is itself scanned, and a committed
# well-formed credential is a real finding for the scanner it is testing. The
# obvious shortcut is to exempt the test file from the scan, and that would
# leave the easiest possible hole in a security control - an attacker commits a
# key next to the fixtures and the scan waves it through. So the fixtures are
# split instead, and the split is invisible to the scan while the
# concatenation is a real credential by the time it reaches the throwaway repo.
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${TMPDIR:-/tmp}/secretscan-proof-$$"
rm -rf "$WORK"; mkdir -p "$WORK"
pass=0; fail=0
ck() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; pass=$((pass+1)); else echo "  FAIL  $1 (expected $2 got $3)"; fail=$((fail+1)); fi; }

# --- fixture fragments, assembled on use -------------------------------------
# The scheme is split as well as the host. Interpolating only the host still
# leaves a scheme, credentials and an at-sign followed by a dollar-brace on one
# line, which the scanner reads as a routable URI whose host is the literal
# text of the variable reference. Writing that example out in full here, as a
# comment explaining the problem, was enough to trip it.
atlas_scheme="mongodb+srv:"
atlas_tld="mongodb"
leak_host="cluster0.abcd1.${atlas_tld}.net"
leak_uri="${atlas_scheme}//admin:S3cr3tP4ss@${leak_host}/companero"

aws_key="AKIA""IOSFODNN7EXAMPLE"
pk_head="-----BEGIN RSA "
pk_tail="PRIVATE"" KEY-----"
gh_token="gh""p_0123456789abcdefghijklmnopqrstuvwxyz"
slack_token="xox""b-123456789012-abcdefghijkl"

# A secret field name, split so the harness does not itself trip the
# "configuration must never reach a log call" rule.
secret_field="jwt""Secret"

seed() {
  rm -rf "$WORK/r"; mkdir -p "$WORK/r"
  git -C "$WORK/r" init -q
  git -C "$WORK/r" config user.email t@t.local
  git -C "$WORK/r" config user.name T
  cp "$SRC/.env.example" "$WORK/r/.env.example"
  cp "$SRC/infrastructure/scripts/secret-scan.sh" "$WORK/r/scan.sh"
  # Minimal canary fixture so section 4's "canaries must exist" guard is met.
  mkdir -p "$WORK/r/src/test"
  echo 'val uri = "mongodb+srv://user:hunter2@cluster.example.edu/db"' > "$WORK/r/src/test/Canary.kt"
  git -C "$WORK/r" add -A >/dev/null
  git -C "$WORK/r" commit -q -m seed
}
run() { ( cd "$WORK/r" && bash scan.sh ) 2>&1; }
plant() { echo "$2" > "$WORK/r/$1"; git -C "$WORK/r" add -A >/dev/null; git -C "$WORK/r" commit -q -m "$3"; }

echo "CASE 1: clean repository passes"
seed
out=$(run); ck "exit 0" "0" "$?"
echo "$out" | grep -q "SECRET SCAN: PASS" && ck "reports PASS" yes yes || ck "reports PASS" yes no

echo "CASE 2: a real-looking Atlas host is caught"
seed
plant Leak.kt "val uri = \"$leak_uri\"" leak
out=$(run); ck "exit non-zero" "1" "$?"
echo "$out" | grep -q "real-looking host: $leak_host" && ck "names the host" yes yes || ck "names the host" yes no
echo "$out" | grep -q "SECRET SCAN: FAIL" && ck "reports FAIL" yes yes || ck "reports FAIL" yes no

echo "CASE 3: a committed .env is caught"
seed
cp "$WORK/r/.env.example" "$WORK/r/.env"
git -C "$WORK/r" add -A >/dev/null; git -C "$WORK/r" commit -q -m env
out=$(run); ck "exit non-zero" "1" "$?"
echo "$out" | grep -q "\.env is tracked" && ck "names the file" yes yes || ck "names the file" yes no

echo "CASE 4: key material is caught"
seed
plant release.jks "not-a-real-key" jks
out=$(run); ck "exit non-zero" "1" "$?"
echo "$out" | grep -q "release.jks" && ck "names the file" yes yes || ck "names the file" yes no

echo "CASE 5: a real secret in .env.example is caught"
seed
sed -i "s|^MONGODB_URI=.*|MONGODB_URI=$leak_uri|" "$WORK/r/.env.example"
grep -q "$leak_host" "$WORK/r/.env.example" || { echo "  setup error: leak not planted"; exit 1; }
git -C "$WORK/r" add -A >/dev/null; git -C "$WORK/r" commit -q -m envval
out=$(run); ck "exit non-zero" "1" "$?"
echo "$out" | grep -q "real-looking host" && ck "names the host" yes yes || ck "names the host" yes no

echo "CASE 6: a secret logged at runtime is caught"
seed
mkdir -p "$WORK/r/svc"
cat > "$WORK/r/svc/Log.kt" <<KT
fun log(settings: ApiSettings) {
    log.info("booting with {}", settings.$secret_field)
}
KT
git -C "$WORK/r" add -A >/dev/null; git -C "$WORK/r" commit -q -m log
out=$(run); ck "exit non-zero" "1" "$?"
echo "$out" | grep -q "secret field is passed to a log call" && ck "names the leak" yes yes || ck "names the leak" yes no

echo "CASE 7: removing the leak restores PASS"
seed
out=$(run); ck "exit 0 again" "0" "$?"

# The cases below exist because section 3 used to print these hits and then
# declare them "classified in section 4", which never classified them. The scan
# exited PASS with a real AWS key in the tree. A control that reports a leak
# and passes is worse than one that stays quiet, so each format gets a case.

expect_fail_on() {
  local label="$1" file="$2" content="$3" marker="$4"
  seed
  plant "$file" "$content" "$label"
  out=$(run); ck "$label: exit non-zero" "1" "$?"
  if echo "$out" | grep -q "$marker"; then
    ck "$label: explains why" yes yes
  else
    ck "$label: explains why" yes "no"
    echo "$out" | sed 's/^/        | /'
  fi
}

echo "CASE 8: a committed AWS access key id is caught"
expect_fail_on "aws-key" Leak.kt "val key = \"$aws_key\"" \
  "provider-issued credential is committed"

echo "CASE 9: a committed private key body is caught"
expect_fail_on "private-key" Leak.kt "val pem = \"$pk_head$pk_tail\"" \
  "provider-issued credential is committed"

echo "CASE 10: a committed GitHub token is caught"
expect_fail_on "github-token" Leak.kt "val t = \"$gh_token\"" \
  "provider-issued credential is committed"

echo "CASE 11: a committed Slack token is caught"
expect_fail_on "slack-token" Leak.kt "val t = \"$slack_token\"" \
  "provider-issued credential is committed"

echo "CASE 12: this harness is not a finding against itself"
# The fixtures above are assembled from fragments precisely so that the scanner
# run over the real repository stays clean. If a future edit pastes one of them
# back in as a literal, this is the assertion that says so.
out=$(cd "$SRC" && bash infrastructure/scripts/secret-scan.sh 2>&1)
ck "the real repository still passes" "0" "$?"

rm -rf "$WORK"
echo ""
echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
