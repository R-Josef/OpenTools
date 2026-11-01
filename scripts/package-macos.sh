#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: package-macos.sh x64|arm64 [package-unix options]\n' >&2
}

[[ $# -gt 0 ]] || { usage; exit 2; }
architecture="$1"
shift

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "$architecture" in
    x64|arm64)
        exec bash "$script_dir/package-unix.sh" \
            --manifest "$script_dir/../packaging/macos-$architecture/tools.json" \
            "$@"
        ;;
    *)
        usage
        exit 2
        ;;
esac
