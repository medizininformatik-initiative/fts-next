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

    # Check URL availability
    if curl --retry 3 --max-time 2 -fsL --head "$url" >/dev/null 2>&1; then
        echo "✓ URL is available: $url"
        return 0
    else
        # Check if this URL is in the required list
        if url_matches_list "$url" "$required_urls"; then
          echo "✗ URL is unavailable: $url"
          return 1  # Exit with error for required URLs
        else
          echo "⚠ Optional URL is unavailable: $url" >&2
          return 0  # Don't fail for optional URLs
        fi
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
