#!/usr/bin/env bash
# Classifies every credential-shaped string in the tracked tree.
#
# A scan that only reports hits is useless on a repository that deliberately
# contains canary credentials: the output has to separate "a real key" from "a
# fixture whose purpose is to prove the scanner and the code would catch it".
# Each category is classified explicitly, and anything unrecognised is reported
# rather than waved through.
#
# Two bugs this script had to be rewritten around, both worth remembering:
#
#   1. `\+` is unreliable inside git grep's ERE. `mongodb\+srv` matches, but
#      `mongodb\+srv://` silently matches nothing, which made the one real leak
#      pattern in this file unmatchable. A literal plus is written `[+]`.
#   2. `fail=1` inside a `while read` fed by a pipeline runs in a subshell and
#      never reaches the parent, so the script printed FAIL three times and
#      still exited PASS. Verdict flags are now accumulated in a file and
#      folded in after the loop.
set -uo pipefail

fail=0
note() { printf '  %-8s %s\n' "$1" "$2"; }
srv_re='mongodb[+]srv://[^:[:space:]]+:[^@[:space:]]+@'
# Same URI, but the match has to carry the host as well, otherwise there is
# nothing left to classify after splitting on '@'.
srv_host_re='mongodb[+]srv://[^@[:space:]]+@[^/?[:space:]]+'

echo "=== 1. key material committed ==="
if git ls-files | grep -Eqi '\.(pem|key|p12|jks|keystore|ppk|asc|gpg)$'; then
  git ls-files | grep -Ei '\.(pem|key|p12|jks|keystore|ppk|asc|gpg)$' | sed 's/^/  FAIL    /'
  fail=1
else
  note clean "no key material tracked"
fi

echo
echo "=== 2. .env handling ==="
if git ls-files --error-unmatch .env >/dev/null 2>&1; then
  note FAIL ".env is tracked"
  fail=1
else
  note clean ".env not tracked"
fi
git ls-files --error-unmatch .env.example >/dev/null 2>&1 \
  && note ok ".env.example is tracked" \
  || { note FAIL ".env.example missing"; fail=1; }

echo
echo "=== 3. provider-issued credential formats ==="
# This used to print hits and then say "classified in section 4", but section 4
# only ever classified mongodb+srv URIs. An AKIA key, a private key body or a
# GitHub token in a .kt file was printed as information and the scan still
# exited PASS: a security check that reports a real key and calls it a pass.
#
# There is no allowlist here on purpose. Nothing in this tree legitimately
# contains a provider-issued credential, so any hit is a leak. The deliberate
# fixtures are placeholders, not well-formed credentials, and the one real leak
# shape (an Atlas URI) is classified in section 4.
other_re='AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{30,}|xox[baprs]-[A-Za-z0-9-]{10,}'
if other=$(git grep -nIE "$other_re" -- . 2>/dev/null); then
  printf '%s\n' "$other" | sed 's/^/  FAIL    /'
  note FAIL "a provider-issued credential is committed to the tree"
  fail=1
else
  note clean "no AWS key, private key, GitHub token or Slack token present"
fi

echo
echo "=== 4. classification of every mongodb+srv URI found ==="
uris=$(git grep -ohIE "$srv_host_re" -- . 2>/dev/null | sort -u)
if [ -z "$uris" ]; then
  note clean "no mongodb+srv URI present"
else
  unclassified=0
  canaries=0
  # `while ... done <<< "$uris"` keeps the loop in the current shell, so the
  # counters below are real.
  while IFS= read -r uri; do
    [ -n "$uri" ] || continue
    host="${uri##*@}"
    host="${host%%/*}"
    host="${host%%\?*}"
    case "$host" in
      '<cluster-host>')
        note canary "placeholder in .env.example"
        canaries=$((canaries + 1))
        ;;
      *example*|*.example.edu)
        note canary "non-routable example host: $host"
        canaries=$((canaries + 1))
        ;;
      '')
        note FAIL "could not parse a host out of: $uri"
        unclassified=$((unclassified + 1))
        ;;
      *)
        note FAIL "real-looking host: $host  <-- investigate"
        unclassified=$((unclassified + 1))
        ;;
    esac
  done <<< "$uris"

  note info  "$canaries canaries classified, $unclassified unclassified"
  [ "$canaries" -gt 0 ] || { note FAIL "expected the leak canaries to still exist"; unclassified=$((unclassified + 1)); }
  [ "$unclassified" -eq 0 ] || fail=1
fi

echo
echo "=== 5. secret-bearing variables in .env.example must be empty or placeholders ==="
# Only variables that are actually secret. Non-secret defaults such as
# APP_ENV=local or API_PORT=8080 are useful documentation, not leaks.
bad=$(grep -E '^[A-Z_]*(SECRET|PASSWORD|TOKEN|URI|KEY)[A-Z_]*=' .env.example 2>/dev/null \
      | grep -vE '=\s*$|=<[^>]*>|\$\{')
if [ -n "$bad" ]; then
  echo "$bad" | sed 's/^/  FAIL    /'
  fail=1
else
  note clean "every secret-bearing variable is empty or a placeholder"
  n=$(grep -cE '^[A-Z_]+=.+$' .env.example 2>/dev/null || echo 0)
  note info  "$n non-secret defaults carry real values on purpose"
fi

echo
echo "=== 6. configuration must never reach a log call ==="
if git grep -nIE '(log|logger|println)[.(][^)]*\b(jwtSecret|mongoUri|mongoSettings|password|token)\b' -- '*.kt' 2>/dev/null; then
  note FAIL "a secret field is passed to a log call"
  fail=1
else
  note clean "no secret field reaches a log call"
fi

echo
if [ "$fail" -eq 0 ]; then echo "SECRET SCAN: PASS"; else echo "SECRET SCAN: FAIL"; fi
exit "$fail"
