#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    printf 'Usage: build-meme-cygwin.sh OUTPUT_DIR\n'
    printf 'Environment: MEME_VERSION (default 5.5.9), MEME_SOURCE_ARCHIVE, MEME_SHA256,\n'
    printf '             MEME_BUILD_JOBS. Runs under Cygwin.\n'
    exit 0
fi

output_dir="${1:?Usage: build-meme-cygwin.sh OUTPUT_DIR}"
version="${MEME_VERSION:-5.5.9}"
jobs="${MEME_BUILD_JOBS:-$(nproc)}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
work_dir="${MEME_BUILD_DIR:-$project_root/target/meme-cygwin-$version}"
archive="${MEME_SOURCE_ARCHIVE:-$work_dir/meme-$version.tar.gz}"
expected_sha="${MEME_SHA256:-}"
patch_file="$script_dir/patches/meme-$version-cygwin.patch"

case "$(uname -s)" in
    CYGWIN*) ;;
    *) printf 'build-meme-cygwin.sh must run under Cygwin.\n' >&2; exit 2 ;;
esac
for command in curl tar make gcc patch sha256sum; do
    command -v "$command" >/dev/null 2>&1 || {
        printf 'Required command is missing: %s\n' "$command" >&2
        exit 2
    }
done
[[ -f "$patch_file" ]] || { printf 'Missing patch: %s\n' "$patch_file" >&2; exit 2; }

mkdir -p "$work_dir"
if [[ ! -f "$archive" ]]; then
    curl --fail --location --retry 3 --output "$archive" \
        "https://meme-suite.org/meme/meme-software/$version/meme-$version.tar.gz"
fi
if [[ -n "$expected_sha" ]]; then
    actual="$(sha256sum "$archive" | awk '{print tolower($1)}')"
    [[ "$actual" == "${expected_sha,,}" ]] || {
        printf 'SHA-256 mismatch for %s: %s\n' "$archive" "$actual" >&2
        exit 2
    }
fi

rm -rf "$work_dir/source" "$work_dir/install"
mkdir -p "$work_dir/source"
tar xzf "$archive" -C "$work_dir/source"
source_dir="$work_dir/source/meme-$version"
[[ -d "$source_dir" ]] || { printf 'MEME source directory is missing.\n' >&2; exit 2; }

(
    cd "$source_dir"
    patch -p1 < "$patch_file"
    ./configure --prefix="$work_dir/install" \
        --enable-build-libxml2 --enable-build-libxslt
    make -j"$jobs"
    make install
)

rm -rf "$output_dir"
mkdir -p "$output_dir/bin" "$output_dir/lib" "$output_dir/libexec" \
    "$output_dir/share/meme-$version"
cp -a "$work_dir/install/bin/." "$output_dir/bin/"
if [[ -d "$work_dir/install/lib" ]]; then
    cp -a "$work_dir/install/lib/." "$output_dir/lib/"
fi
cp -a "$work_dir/install/libexec/." "$output_dir/libexec/"
cp -a "$work_dir/install/share/meme-$version/." "$output_dir/share/meme-$version/"
cp -a /usr/bin/cygwin1.dll "$output_dir/bin/"
if [[ -f "$source_dir/LICENSE" ]]; then
    cp -a "$source_dir/LICENSE" "$output_dir/LICENSE"
fi

{
    printf 'memeVersion=%s\n' "$version"
    printf 'buildDate=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'platform=%s\n' "$(uname -s)"
    printf 'architecture=%s\n' "$(uname -m)"
    printf 'compiler=%s\n' "$(gcc --version | head -n 1)"
    printf 'patch=%s\n' "$(basename "$patch_file")"
    printf 'jobs=%s\n' "$jobs"
} > "$output_dir/BUILD-INFO.txt"

PATH="$output_dir/bin:$PATH" "$output_dir/bin/meme.exe" -version
PATH="$output_dir/bin:$PATH" "$output_dir/bin/mast.exe" -version
printf 'Built MEME %s for Cygwin into %s\n' "$version" "$output_dir"
