#!/usr/bin/env bash
# Scans the working tree for committed secrets: keystores, private keys, credential files, .env files,
# cloud/API key patterns and hard-coded password assignments. Exits non-zero when anything is found.
#
# Usage: scripts/check-secrets.sh [root-directory]     (default: the repository root)
#
# Output lists "file:line: rule" only; matched text is never printed, so the log cannot leak a secret.
#
# Allowlisting:
#   * scripts/check-secrets.allowlist - one path glob per line (relative to the root, '#' comments allowed).
#     Matching files are exempt from the *weak* content rules (password/secret assignments). They are still
#     scanned for private keys and well-known token formats. Use it for docs that mention secret NAMES.
#   * A line containing "check-secrets:allow" is skipped by the content rules.
set -uo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="${1:-$(cd "$script_dir/.." && pwd)}"
allowlist="$script_dir/check-secrets.allowlist"

if [ ! -d "$root" ]; then
  echo "check-secrets: not a directory: $root" >&2
  exit 2
fi
cd "$root" || exit 2

prune_dirs=(.git .gradle .kotlin .idea build node_modules dist captures .cxx .externalNativeBuild)
findings=0

report() {
  printf 'FOUND  %s: %s\n' "$1" "$2"
  findings=$((findings + 1))
}

find_args=()
for d in "${prune_dirs[@]}"; do
  find_args+=(-name "$d" -o)
done
unset 'find_args[${#find_args[@]}-1]'

is_allowlisted() {
  [ -f "$allowlist" ] || return 1
  local pattern
  while IFS= read -r pattern || [ -n "$pattern" ]; do
    pattern="${pattern%%#*}"
    pattern="${pattern//[[:space:]]/}"
    [ -z "$pattern" ] && continue
    # shellcheck disable=SC2254 # the glob is intentionally taken from the allowlist
    case "$1" in $pattern) return 0 ;; esac
  done < "$allowlist"
  return 1
}

# --- 1. Suspicious file names -----------------------------------------------------------------------------------
while IFS= read -r -d '' file; do
  file="${file#./}"
  base="${file##*/}"
  case "$base" in
    .env.example) continue ;;
    *.jks | *.keystore | *.p12 | *.pfx | *.pem | *.key | *.pk8 | *.ppk | *.gpg) report "$file" "key/keystore file" ;;
    .env | .env.*) report "$file" ".env file" ;;
    keystore.properties | signing.properties) report "$file" "signing properties file" ;;
    google-services.json | GoogleService-Info.plist) report "$file" "service configuration file" ;;
    *credentials*.json | service-account*.json | client_secret*.json) report "$file" "credential JSON file" ;;
    id_rsa* | id_dsa* | id_ecdsa* | id_ed25519*) report "$file" "SSH private key file name" ;;
  esac
done < <(find . \( "${find_args[@]}" \) -prune -o -type f -print0)

# --- 2. Content rules ---------------------------------------------------------------------------------------------
# strong rules: applied to every text file, never allowlisted by path
strong_rules=(
  'private key block|-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED|PGP) )?PRIVATE KEY'
  'AWS access key id|(AKIA|ASIA)[0-9A-Z]{16}'
  'Google API key|AIza[0-9A-Za-z_-]{35}'
  'secret-key style token (sk-)|sk-[A-Za-z0-9_-]{20,}'
  'GitHub token|(gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{20,})'
  'Slack token|xox[baprs]-[A-Za-z0-9-]{10,}'
  'Google OAuth client secret|GOCSPX-[A-Za-z0-9_-]{20,}'
  'JSON service account private_key|"private_key"[[:space:]]*:[[:space:]]*"'
)
# weak rules: hard-coded literals assigned to password/secret/token-like names; skipped for allowlisted paths.
# The unquoted rule targets .properties/.env style lines (key=value with a bare value), not code expressions.
weak_rules=(
  'quoted secret literal|(password|passwd|passphrase|secret|api_?key|access_?token|auth_?token)[A-Za-z_]*[[:space:]]*[=:][[:space:]]*["'"'"'][^"'"'"'$<{ ]{4,}["'"'"']'
  'unquoted secret assignment|^[[:space:]]*[A-Za-z_.]*(password|passwd|secret|api_?key|token)[A-Za-z_.]*[[:space:]]*=[[:space:]]*[A-Za-z0-9_+/=-]{6,}[[:space:]]*$'
)

scan() { # scan <kind> <rules...>
  local kind="$1"
  shift
  local rule name regex file lineno
  for rule in "$@"; do
    name="${rule%%|*}"
    regex="${rule#*|}"
    while IFS=: read -r file lineno _; do
      file="${file#./}"
      [ "$file" = "scripts/check-secrets.sh" ] && continue
      if [ "$kind" = weak ] && is_allowlisted "$file"; then continue; fi
      if sed -n "${lineno}p" "$file" | grep -q 'check-secrets:allow'; then continue; fi
      report "$file:$lineno" "$name"
    done < <(grep -rInEi "${prune_dirs[@]/#/--exclude-dir=}" -e "$regex" . 2>/dev/null | cut -d: -f1,2)
  done
}

scan strong "${strong_rules[@]}"
scan weak "${weak_rules[@]}"

if [ "$findings" -gt 0 ]; then
  echo "check-secrets: $findings finding(s). Remove the secret, rotate it if it was ever pushed, or allowlist it (see header)." >&2
  exit 1
fi
echo "check-secrets: no secrets found in $root"
