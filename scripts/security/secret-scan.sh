#!/usr/bin/env bash
set -euo pipefail
shopt -s nocasematch

root="${1:-.}"
found=0

scan_file() {
  local file="$1"
  local line_number=0
  local line
  local type

  while IFS= read -r line || [ -n "$line" ]; do
    line_number=$((line_number + 1))
    type=""

    if [[ "$line" =~ -----BEGIN[[:space:]]+([A-Z]+[[:space:]])?PRIVATE[[:space:]]+KEY----- ]]; then
      type="private_key_header"
    elif [[ "$line" =~ (^|[^A-Za-z0-9_])(aws_access_key_id|aws_secret_access_key|api[_-]?key|password|secret|token)[[:space:]]*[:=][[:space:]]*[\"\']?\$\{[A-Z0-9_]+(:[^}]*)?\}[\"\']? ]]; then
      type=""
    elif [[ "$line" =~ (^|[^A-Za-z0-9_])(aws_access_key_id|aws_secret_access_key|api[_-]?key|password|secret|token)[[:space:]]*[:=][[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*$ ]]; then
      type=""
    elif [[ "$line" =~ (^|[^A-Za-z0-9_])(aws_access_key_id|aws_secret_access_key|api[_-]?key|password|secret|token)[[:space:]]*[:=][[:space:]]*[\"\']?[^[:space:]\"\']{8,}[\"\']? ]]; then
      type="${BASH_REMATCH[2]}"
    fi

    if [ -n "$type" ]; then
      printf '%s:%s:%s\n' "$file" "$line_number" "$type"
      found=1
    fi
  done < "$file"
}

while IFS= read -r -d '' file; do
  scan_file "$file"
done < <(
  find "$root" -type f \
    -not -path '*/.git/*' \
    -not -path '*/backend/build/*' \
    -not -path '*/backend/.gradle/*' \
    -not -path '*/node_modules/*' \
    -not -path '*/dist/*' \
    -not -name '.env.example' \
    -print0
)

exit "$found"
