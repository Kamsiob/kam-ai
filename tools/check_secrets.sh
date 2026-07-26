#!/usr/bin/env bash
# Fails if anything secret-shaped is tracked by git, or if a credential
# identifier has crept back into a tracked file.
#
# Runs in CI on every push and pull request, and is worth running by hand before
# a release. It checks what git actually tracks rather than what is on disk, so
# an ignored file sitting locally is fine and the same file staged is not.
set -euo pipefail

fail=0
note() { printf '  %s\n' "$*" >&2; fail=1; }

# 1. Files that must never be tracked, by name.
while IFS= read -r f; do
  case "$f" in
    *.jks|*.keystore|*.p12|*.pfx|*.pem|*.key) note "tracked key material: $f" ;;
    *keystore.properties|*signing.properties|*local.properties) note "tracked signing config: $f" ;;
    *service-account*.json|*service_account*.json|google-services.json) note "tracked service account: $f" ;;
  esac
done < <(git ls-files)

# 2. Credential-shaped content in tracked files. The patterns are the ones that
#    are unambiguous: a private key block, a provider token prefix, or a
#    password assigned a literal rather than read from somewhere.
if git grep -nIE "BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY" -- . >/dev/null 2>&1; then
  note "a private key block is committed"
fi
for pat in 'ghp_[A-Za-z0-9]{20,}' 'gho_[A-Za-z0-9]{20,}' 'github_pat_[A-Za-z0-9_]{20,}' 'AKIA[0-9A-Z]{16}'; do
  if git grep -nIE "$pat" -- . >/dev/null 2>&1; then note "token-shaped string matching $pat"; fi
done
# A password given a literal value, as opposed to getProperty/env lookups.
if git grep -nIE '(storePassword|keyPassword|passphrase)\s*=\s*"[^"]{3,}"' -- '*.kts' '*.gradle' '*.properties' >/dev/null 2>&1; then
  note "a signing password appears to be hardcoded"
fi

# 3. Identifiers that name the publishing account. Not credentials, and not
#    usable on their own, but they name the target and there is no reason for
#    them to be public. Removed once already; this stops them returning.
if git grep -nIE "iam\.gserviceaccount\.com" -- . >/dev/null 2>&1; then
  note "a service account email is committed"
fi

if [ "$fail" -ne 0 ]; then
  echo "" >&2
  echo "Secret check failed. Nothing above may be committed to a public repository." >&2
  exit 1
fi
echo "Secret check passed: nothing secret-shaped is tracked."
