#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  >&2 echo "JSON config file must be passed as first argument"
  exit 2
fi

config_file="$1"
max_parallel=${2:-10}  # Default to 10 parallel processes

# Check if config file exists
if [ ! -f "$config_file" ]; then
    >&2 echo "Error: Config file '$config_file' not found"
    exit 2
fi

# Read configuration
files=$(jq -r '.files[]' "$config_file")
required_urls=$(jq -r '.required[]' "$config_file")
ignored_urls=$(jq -r '.ignored[]' "$config_file")

# Track if we should fail at the end
should_fail=false

# Function to check if URL matches any pattern in a list
url_matches_list() {
    local url="$1"
    local list="$2"

    while IFS= read -r pattern; do
        if [[ "$url" == *"$pattern"* ]]; then
            return 0
        fi
    done <<< "$list"
    return 1
}

# Function to check a single URL - will be called by xargs
check_url() {
    local url="$1"
    local required_urls="$2"
    local ignored_urls="$3"

    # Check if URL should be ignored
    if url_matches_list "$url" "$ignored_urls"; then
        return 0
    fi

    # Check URL availability. A fast HEAD probe handles the common case; on any
    # failure (transient WAF 403/429, slow TLS, or servers that reject HEAD with
    # 405) fall back to a robust ranged GET that retries with backoff. A
    # browser user-agent avoids reputation-based bot blocking by external hosts.
    # -S makes curl emit the underlying error despite -s, so a hard failure can
    # report *why* (HTTP status / connection error) instead of a bare url.
    local ua="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    local common=(-A "$ua" -fsSL --connect-timeout 10 --max-time 20)
    if curl "${common[@]}" -I "$url" >/dev/null 2>&1; then
        echo "✓ URL is available: $url"
        return 0
    fi

    # Capture the fallback's stderr (retry warnings + final error) so we can
    # surface curl's diagnostic on failure.
    local err
    if err=$(curl "${common[@]}" -r 0-0 \
                 --retry 5 --retry-all-errors --retry-delay 1 --retry-max-time 30 \
                 "$url" 2>&1 >/dev/null); then
        echo "✓ URL is available: $url"
        return 0
    fi

    # Collapse the multi-line output to its last meaningful line (the final
    # "curl: (NN) ..." error) for a compact, informative message.
    local detail
    detail=$(printf '%s\n' "$err" | grep -v '^[[:space:]]*$' | tail -n 1) || true

    # Check if this URL is in the required list
    if url_matches_list "$url" "$required_urls"; then
      echo "✗ URL is unavailable: $url${detail:+ — $detail}"
      return 1  # Exit with error for required URLs
    else
      echo "⚠ Optional URL is unavailable: $url${detail:+ — $detail}" >&2
      return 0  # Don't fail for optional URLs
    fi
}

# Export the function and variables so xargs can use them
export -f check_url
export -f url_matches_list
export required_urls
export ignored_urls

while IFS= read -r file; do
    if [ ! -f "$file" ]; then
        echo "⚠ Warning: File '$file' not found, skipping..."
        continue
    fi

    echo "· Processing file: $file"

    # Extract URLs and process them in parallel
    # If xargs returns non-zero, it means at least one required URL failed
    if ! grep -o -E "https?://[^ \"]*" "$file" 2>/dev/null | \
         xargs -I {} -P "$max_parallel" -n 1 bash -c 'check_url "$@"' _ {} "$required_urls" "$ignored_urls"; then
        should_fail=true
    fi
    echo

done <<< "$files"

# Exit with failure if any required URLs were unavailable
if [ "$should_fail" = true ]; then
    echo "=== FAILURE ==="
    >&2 echo "Script failed: One or more required URLs are unavailable"
    exit 1
else
  echo "=== SUCCESS ==="
  echo "All checks completed successfully - all required URLs are available!"
fi
