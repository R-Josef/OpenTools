#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: package-sources.sh [options]

Options:
  --manifest PATH    Source component manifest (default: packaging/sources.json)
  --cache-dir PATH   Source archive cache (default: .release-cache/sources)
  --output-dir PATH  Archive output directory (default: target/release)
  --offline          Fail instead of downloading missing source archives
  --tag TAG          Source revision to record (default: current repository version)
EOF
}

manifest=""
cache_dir=""
output_dir=""
offline=0
tag=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --manifest) manifest="${2:?Missing manifest path}"; shift 2 ;;
        --cache-dir) cache_dir="${2:?Missing cache path}"; shift 2 ;;
        --output-dir) output_dir="${2:?Missing output path}"; shift 2 ;;
        --offline) offline=1; shift ;;
        --tag) tag="${2:?Missing tag}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
manifest="${manifest:-$project_root/packaging/sources.json}"
manifest="$(cd "$(dirname "$manifest")" && pwd)/$(basename "$manifest")"

for command in curl jq tar gzip sha256sum git; do
    command -v "$command" >/dev/null 2>&1 || {
        printf 'Required command is missing: %s\n' "$command" >&2
        exit 2
    }
done

version="$(
    sed -n 's:^[[:space:]]*<version>\([^<]*\)</version>.*:\1:p' "$project_root/pom.xml" |
        head -n 1 |
        tr -d '\r'
)"
[[ -n "$version" ]] || {
    printf 'Could not resolve the project version from pom.xml.\n' >&2
    exit 2
}
tag="${tag:-$version}"
cache_dir="${cache_dir:-$project_root/.release-cache/sources}"
output_dir="${output_dir:-$project_root/target/release}"
work="$project_root/target/release-work-sources"
bundle_name="opentools-$version-corresponding-sources"
bundle_root="$work/$bundle_name"
archive="$output_dir/$bundle_name.tar.gz"

mkdir -p "$cache_dir" "$output_dir"
rm -rf "$work"
mkdir -p "$bundle_root/third-party"

hash_file() {
    sha256sum "$1" | awk '{print tolower($1)}'
}

jq_value() {
    local filter="$1" input="$2"
    jq -r "$filter" <<<"$input" | tr -d '\r'
}

get_source() {
    local file="$1" url="$2" expected="$3" path
    [[ "$file" != */* && "$file" != *\\* ]] || {
        printf 'Unsafe source archive name: %s\n' "$file" >&2
        exit 2
    }
    path="$cache_dir/$file"
    if [[ ! -f "$path" ]]; then
        if [[ "$offline" -eq 1 ]]; then
            printf 'Offline source packaging is missing archive: %s\n' "$file" >&2
            exit 2
        fi
        curl -fL --retry 4 --retry-delay 2 --output "$path.download" "$url"
        mv "$path.download" "$path"
    fi
    local actual expected_lower
    actual="$(hash_file "$path")"
    expected_lower="$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')"
    [[ "$actual" == "$expected_lower" ]] || {
        printf 'SHA-256 mismatch for %s. Expected %s, found %s.\n' \
            "$file" "$expected_lower" "$actual" >&2
        exit 2
    }
    printf '%s' "$path"
}

git archive --format=tar --prefix="opentools-$version/" HEAD |
    gzip -n > "$bundle_root/opentools-$version-source.tar.gz"

while IFS= read -r component; do
    id="$(jq_value '.id' "$component")"
    file="$(jq_value '.archive.file' "$component")"
    url="$(jq_value '.archive.url' "$component")"
    expected="$(jq_value '.archive.sha256' "$component")"
    source_path="$(get_source "$file" "$url" "$expected")"
    mkdir -p "$bundle_root/third-party/$id"
    cp "$source_path" "$bundle_root/third-party/$id/$file"
    while IFS= read -r repo_file; do
        source_file="$(jq_value '.source' "$repo_file")"
        destination="$(jq_value '.destination' "$repo_file")"
        [[ -f "$project_root/$source_file" ]] || {
            printf 'Source component file is missing: %s\n' "$source_file" >&2
            exit 2
        }
        mkdir -p "$bundle_root/third-party/$id/$(dirname "$destination")"
        cp "$project_root/$source_file" "$bundle_root/third-party/$id/$destination"
    done < <(jq -c '.repoFiles[]?' <<<"$component")
done < <(jq -c '.components[]' < "$manifest")

cp "$manifest" "$bundle_root/sources.json"
cp "$project_root/LICENSE" "$project_root/NOTICE" \
    "$project_root/THIRD_PARTY_NOTICES.md" "$bundle_root/"
cp "$project_root/licenses/SOURCE_INFO.md" "$bundle_root/"
mkdir -p "$bundle_root/licenses"
cp "$project_root"/licenses/*.txt "$bundle_root/licenses/"

commit="$(git rev-parse HEAD)"
cat > "$bundle_root/README.md" <<EOF
# OpenTools corresponding sources

This archive accompanies the OpenTools $version binary release.

- OpenTools source revision: \`$commit\`
- Release tag: \`$tag\`
- Component inventory: \`sources.json\`
- License and source guidance: \`SOURCE_INFO.md\`

The archive contains the source revision, required third-party source
archives, exact SHA-256 values, and the MEME build patch used for the release.
EOF

rm -f "$archive"
tar --owner=0 --group=0 --numeric-owner -czf "$archive" -C "$work" "$bundle_name"

archive_hash="$(hash_file "$archive" | awk '{print toupper($1)}')"
archive_bytes="$(wc -c < "$archive" | tr -d ' ')"
components_json="$(jq -c '.components' < "$manifest")"
jq -n \
    --arg package "$(basename "$archive")" \
    --arg sha256 "$archive_hash" \
    --argjson bytes "$archive_bytes" \
    --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sourceRevision "$commit" \
    --arg releaseTag "$tag" \
    --argjson components "$components_json" \
    '{
      package: $package,
      sha256: $sha256,
      bytes: $bytes,
      builtAt: $builtAt,
      sourceRevision: $sourceRevision,
      releaseTag: $releaseTag,
      components: $components
    }' > "$archive.sha256.json"

rm -rf "$work"
printf '%s\n%s\n' "$archive" "$archive_hash"
