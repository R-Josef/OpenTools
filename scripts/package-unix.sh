#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: package-unix.sh --manifest PATH [options]

Options:
  --cache-dir PATH         Artifact cache (default: .release-cache/<release>)
  --native-build-dir PATH  Reuse prebuilt HMMER, trimAl, and MCScanX binaries
  --output-dir PATH        Archive output directory (default: target/release)
  --offline                Fail instead of downloading missing artifacts
  --skip-build             Reuse the existing Maven shaded JAR
  --keep-work              Keep the staging directory
EOF
}

manifest=""
cache_dir=""
native_build_dir=""
output_dir=""
offline=0
skip_build=0
keep_work=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --manifest) manifest="${2:?Missing manifest path}"; shift 2 ;;
        --cache-dir) cache_dir="${2:?Missing cache path}"; shift 2 ;;
        --native-build-dir) native_build_dir="${2:?Missing native build path}"; shift 2 ;;
        --output-dir) output_dir="${2:?Missing output path}"; shift 2 ;;
        --offline) offline=1; shift ;;
        --skip-build) skip_build=1; shift ;;
        --keep-work) keep_work=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
done

[[ -n "$manifest" ]] || { usage >&2; exit 2; }

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
manifest="$(cd "$(dirname "$manifest")" && pwd)/$(basename "$manifest")"

for command in jq curl tar unzip mvn java file; do
    command -v "$command" >/dev/null 2>&1 || {
        printf 'Required command is missing: %s\n' "$command" >&2
        exit 2
    }
done

platform="$(jq -r '.platform' "$manifest")"
architecture="$(jq -r '.architecture' "$manifest")"
release="$(jq -r '.release' "$manifest")"
[[ "$release" =~ ^[a-z0-9-]+$ ]] || { printf 'Unsafe release identifier: %s\n' "$release" >&2; exit 2; }
case "$platform" in
    linux)
        [[ "$(uname -s)" == "Linux" ]] || { printf 'This manifest requires Linux.\n' >&2; exit 2; }
        ;;
    macos)
        [[ "$(uname -s)" == "Darwin" ]] || { printf 'This manifest requires macOS.\n' >&2; exit 2; }
        ;;
    *) printf 'Unsupported platform in manifest: %s\n' "$platform" >&2; exit 2 ;;
esac

machine="$(uname -m)"
case "$architecture:$machine" in
    x64:x86_64|arm64:arm64|arm64:aarch64) ;;
    *) printf 'Manifest architecture %s does not match host %s.\n' "$architecture" "$machine" >&2; exit 2 ;;
esac

java_home="${JAVA_HOME:-}"
[[ -n "$java_home" ]] || { printf 'JAVA_HOME must point to JDK 21.\n' >&2; exit 2; }
java="$java_home/bin/java"
jpackage="$java_home/bin/jpackage"
[[ -x "$java" && -x "$jpackage" ]] || { printf 'JDK java/jpackage was not found under JAVA_HOME.\n' >&2; exit 2; }
java_version="$("$java" -version 2>&1 | head -n 1)"
[[ "$java_version" == *'"21.'* ]] || { printf 'Packaging requires JDK 21. Found: %s\n' "$java_version" >&2; exit 2; }

cache_dir="${cache_dir:-$project_root/.release-cache/$release}"
output_dir="${output_dir:-$project_root/target/release}"
work="$project_root/target/release-work-$release"
mkdir -p "$cache_dir" "$output_dir"

rm -rf "$work"
mkdir -p "$work"/{extract,jpackage-input,jpackage-output}

if [[ "$skip_build" -eq 0 ]]; then
    (
        cd "$project_root"
        mvn -q -DskipTests package
    )
fi

jar="$project_root/opentools-app/target/opentools.jar"
[[ -f "$jar" ]] || { printf 'Application JAR is missing: %s\n' "$jar" >&2; exit 2; }
cp "$jar" "$work/jpackage-input/opentools.jar"

version="$(cd "$project_root" && mvn -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1 | tr -d '\r')"
[[ -n "$version" ]] || { printf 'Could not resolve Maven project version.\n' >&2; exit 2; }
app_version="${version%%-*}"
package_name="opentools-$version-$release"
jpackage_version="$app_version"
if [[ "$platform" == "macos" && "${app_version%%.*}" == "0" ]]; then
    jpackage_version="1"
fi

hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print tolower($1)}'
    else
        shasum -a 256 "$1" | awk '{print tolower($1)}'
    fi
}

md5_file() {
    if command -v md5sum >/dev/null 2>&1; then
        md5sum "$1" | awk '{print tolower($1)}'
    else
        md5 -q "$1" | awk '{print tolower($1)}'
    fi
}

artifact_path() {
    local file="$1"
    [[ "$file" != */* && "$file" != *\\* ]] || {
        printf 'Unsafe artifact file name: %s\n' "$file" >&2
        exit 2
    }
    printf '%s/%s' "$cache_dir" "$file"
}

get_artifact() {
    local file="$1" url="$2" expected_sha="$3" expected_md5="$4" path
    path="$(artifact_path "$file")"
    if [[ ! -f "$path" ]]; then
        if [[ "$offline" -eq 1 ]]; then
            printf 'Offline build is missing artifact: %s\n' "$file" >&2
            exit 2
        fi
        curl -fL --retry 4 --retry-delay 2 --output "$path.download" "$url"
        mv "$path.download" "$path"
    fi
    local actual expected
    if [[ -n "$expected_sha" ]]; then
        actual="$(hash_file "$path")"
        expected="$(printf '%s' "$expected_sha" | tr '[:upper:]' '[:lower:]')"
        [[ "$actual" == "$expected" ]] || {
            printf 'SHA-256 mismatch for %s. Expected %s, found %s.\n' "$file" "$expected" "$actual" >&2
            exit 2
        }
    else
        actual="$(md5_file "$path")"
        expected="$(printf '%s' "$expected_md5" | tr '[:upper:]' '[:lower:]')"
        [[ -n "$expected" && "$actual" == "$expected" ]] || {
            printf 'MD5 mismatch for %s. Expected %s, found %s.\n' "$file" "$expected" "$actual" >&2
            exit 2
        }
    fi
    printf '%s' "$path"
}

extract_archive() {
    local path="$1" destination="$2" type="$3"
    mkdir -p "$destination"
    case "$type" in
        tar.gz) tar -xzf "$path" -C "$destination" ;;
        zip) unzip -q "$path" -d "$destination" ;;
        binary) ;;
        *) printf 'Unsupported archive type: %s\n' "$type" >&2; exit 2 ;;
    esac
}

tool_root() {
    local tool_json="$1" id root
    id="$(jq -r '.id' <<<"$tool_json")"
    root="$(jq -r '.archive.root // ""' <<<"$tool_json")"
    if [[ -n "$root" ]]; then
        printf '%s/extract/%s/%s' "$work" "$id" "$root"
    else
        printf '%s/extract/%s' "$work" "$id"
    fi
}

while IFS= read -r tool_json; do
    id="$(jq -r '.id' <<<"$tool_json")"
    file="$(jq -r '.archive.file' <<<"$tool_json")"
    url="$(jq -r '.archive.url' <<<"$tool_json")"
    expected="$(jq -r '.archive.sha256 // ""' <<<"$tool_json")"
    expected_md5="$(jq -r '.archive.md5 // ""' <<<"$tool_json")"
    type="$(jq -r '.archive.type' <<<"$tool_json")"
    artifact="$(get_artifact "$file" "$url" "$expected" "$expected_md5")"
    if [[ "$type" != "binary" ]]; then
        extract_archive "$artifact" "$work/extract/$id" "$type"
    fi
done < <(jq -c '.tools[]' "$manifest")

native="$native_build_dir"
if [[ -z "$native" ]]; then
    hmmer_json="$(jq -c '.tools[] | select(.build == "hmmer")' "$manifest")"
    mcscanx_json="$(jq -c '.tools[] | select(.build == "mcscanx")' "$manifest")"
    trimal_json="$(jq -c '.tools[] | select(.build == "trimal")' "$manifest")"
    [[ -n "$hmmer_json" && -n "$mcscanx_json" && -n "$trimal_json" ]] || {
        printf 'The manifest does not declare all source-built tools.\n' >&2
        exit 2
    }
    native="$work/native"
    HMMER_SOURCE_DIR="$(tool_root "$hmmer_json")" \
    MCSCANX_SOURCE_DIR="$(tool_root "$mcscanx_json")" \
    TRIMAL_SOURCE_DIR="$(tool_root "$trimal_json")/source" \
        bash "$script_dir/build-unix-dependencies.sh" "$native"
else
    native="$(cd "$native" && pwd)"
fi

launcher_properties="$work/OpenToolsCLI.properties"
printf '# OpenTools console launcher\n' > "$launcher_properties"

jpackage_args=(
    --type app-image
    --name OpenTools
    --dest "$work/jpackage-output"
    --input "$work/jpackage-input"
    --main-jar opentools.jar
    --main-class io.opentools.app.OpenToolsLauncher
    --app-version "$jpackage_version"
    --vendor "OpenTools contributors"
    --description "Local-first bioinformatics workbench"
    --copyright "Copyright (C) 2026 OpenTools contributors"
    --java-options -Dfile.encoding=UTF-8
    --java-options -Dopentools.packaged=true
    --add-launcher "OpenToolsCLI=$launcher_properties"
)

if [[ "$platform" == "linux" ]]; then
    jpackage_args+=(--icon "$project_root/opentools-ui/src/main/resources/icons/opentools-256.png")
else
    icon_source="$project_root/assets/branding/opentools-icon-1024.png"
    iconset="$work/OpenTools.iconset"
    mkdir -p "$iconset"
    for size in 16 32 64 128 256 512; do
        sips -z "$size" "$size" "$icon_source" --out "$iconset/icon_${size}x${size}.png" >/dev/null
        double=$((size * 2))
        sips -z "$double" "$double" "$icon_source" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
    done
    iconutil -c icns "$iconset" -o "$work/OpenTools.icns"
    jpackage_args+=(--icon "$work/OpenTools.icns" --mac-package-identifier io.opentools.app)
fi

"$jpackage" "${jpackage_args[@]}"

if [[ "$platform" == "linux" ]]; then
    image_root="$work/jpackage-output/OpenTools"
    data_root="$image_root/lib"
    gui_launcher="$image_root/bin/OpenTools"
    cli_launcher="$image_root/bin/OpenToolsCLI"
    platform_readme="$project_root/packaging/linux/README.txt"
else
    image_root="$work/jpackage-output/OpenTools.app"
    data_root="$image_root/Contents"
    gui_launcher="$data_root/MacOS/OpenTools"
    cli_launcher="$data_root/MacOS/OpenToolsCLI"
    platform_readme="$project_root/packaging/macos/README.txt"
fi
[[ -x "$gui_launcher" && -x "$cli_launcher" ]] || {
    printf 'jpackage did not create expected GUI/CLI launchers.\n' >&2
    exit 2
}

copy_pattern() {
    local source_root="$1" destination_root="$2" spec="$3"
    local pattern="${spec%%|*}" explicit="" relative files destination
    if [[ "$spec" == *"|"* ]]; then explicit="${spec#*|}"; fi
    relative="$(dirname "$pattern")"
    shopt -s nullglob
    files=("$source_root"/$pattern)
    shopt -u nullglob
    [[ ${#files[@]} -gt 0 ]] || {
        printf 'Required copy pattern produced no files: %s\n' "$pattern" >&2
        exit 2
    }
    for file in "${files[@]}"; do
        if [[ -n "$explicit" ]]; then
            destination="$destination_root/$explicit"
        elif [[ "$relative" != "." ]]; then
            destination="$destination_root/$relative/$(basename "$file")"
        else
            destination="$destination_root/$(basename "$file")"
        fi
        mkdir -p "$(dirname "$destination")"
        cp -p "$file" "$destination"
        chmod 0755 "$destination"
    done
}

mkdir -p "$data_root/tools" "$data_root/licenses/tools" "$data_root/licenses/sources"
while IFS= read -r tool_json; do
    id="$(jq -r '.id' <<<"$tool_json")"
    destination="$(jq -r '.destination' <<<"$tool_json")"
    root="$(tool_root "$tool_json")"
    mkdir -p "$data_root/$destination"
    while IFS= read -r spec; do
        if [[ "$spec" == native/* ]]; then
            copy_pattern "$native" "$data_root/$destination" "${spec#native/}"
        elif [[ "$(jq -r '.archive.type' <<<"$tool_json")" == "binary" ]]; then
            copy_pattern "$cache_dir" "$data_root/$destination" "$spec"
        else
            copy_pattern "$root" "$data_root/$destination" "$spec"
        fi
    done < <(jq -r '.copy[]' <<<"$tool_json")
    while IFS= read -r required; do
        [[ -f "$data_root/$destination/$required" ]] || {
            printf '%s is missing required file: %s\n' "$id" "$required" >&2
            exit 2
        }
    done < <(jq -r '.requiredFiles[]' <<<"$tool_json")

    license_dir="$data_root/licenses/tools/$id"
    mkdir -p "$license_dir"
    while IFS= read -r entry; do
        archive_path="$(jq -r '.archivePath' <<<"$entry")"
        license_destination="$(jq -r '.destination' <<<"$entry")"
        mkdir -p "$(dirname "$license_dir/$license_destination")"
        [[ -f "$root/$archive_path" ]] || {
            printf '%s license file is missing: %s\n' "$id" "$archive_path" >&2
            exit 2
        }
        cp "$root/$archive_path" "$license_dir/$license_destination"
    done < <(jq -c '.licenseFiles[]?' <<<"$tool_json")
    if [[ "$id" == "diamond" || "$id" == "muscle" ]]; then
        cp "$project_root/LICENSE" "$license_dir/LICENSE"
    fi
    {
        printf 'tool=%s\n' "$(jq -r '.name' <<<"$tool_json")"
        printf 'version=%s\n' "$(jq -r '.version' <<<"$tool_json")"
        printf 'license=%s\n' "$(jq -r '.license' <<<"$tool_json")"
        printf 'licenseUrl=%s\n' "$(jq -r '.licenseUrl' <<<"$tool_json")"
        printf 'sourceUrl=%s\n' "$(jq -r '.sourceUrl' <<<"$tool_json")"
        printf 'archiveUrl=%s\n' "$(jq -r '.archive.url' <<<"$tool_json")"
        printf 'archiveSha256=%s\n' "$(jq -r '.archive.sha256 // ""' <<<"$tool_json")"
        printf 'archiveMd5=%s\n' "$(jq -r '.archive.md5 // ""' <<<"$tool_json")"
        printf 'build=%s\n' "$(jq -r '.build' <<<"$tool_json")"
        printf 'buildHost=%s %s\n' "$platform" "$architecture"
        printf 'buildDate=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$license_dir/NOTICE.txt"
    if [[ "$(jq -r '.sourceBuilt // false' <<<"$tool_json")" == "true" ]]; then
        file="$(jq -r '.archive.file' <<<"$tool_json")"
        cp "$cache_dir/$file" "$data_root/licenses/sources/$file"
    fi
done < <(jq -c '.tools[]' "$manifest")

cp -R "$project_root/lang" "$data_root/lang"
mkdir -p "$data_root/docs"
cp "$project_root/docs/README.md" "$data_root/docs/"
cp -R "$project_root/docs/en_US" "$data_root/docs/en_US"
cp -R "$project_root/docs/zh_CN" "$data_root/docs/zh_CN"
mkdir -p "$data_root/plugins/data"
cp "$project_root/plugins/README.md" "$data_root/plugins/README.md"
cp "$project_root/plugins/plugin-state.json" "$data_root/plugins/plugin-state.json"
cp "$project_root/opentools-example-plugin/target/opentools-example-plugin.jar" "$data_root/plugins/opentools-example-plugin.jar"
cp -R "$project_root/skills" "$data_root/skills"
cp -R "$project_root/examples" "$data_root/examples"
mkdir -p "$data_root/plugin-sdk"
cp "$project_root/opentools-plugin-api/target/opentools-plugin-api-$version.jar" "$data_root/plugin-sdk/"
cp "$project_root/opentools-plugin-api/target/opentools-plugin-api-$version-javadoc.jar" "$data_root/plugin-sdk/"
cp -R "$project_root/opentools-plugin-api/target/reports/apidocs" "$data_root/plugin-sdk/javadoc"
cp "$project_root/plugin-sdk/README.txt" "$data_root/plugin-sdk/README.txt"
cp "$project_root/LICENSE" "$project_root/NOTICE" "$project_root/THIRD_PARTY_NOTICES.md" "$data_root/"
cp "$platform_readme" "$data_root/README.txt"
if [[ "$platform" == "linux" ]]; then
    cp "$platform_readme" "$image_root/README.txt"
fi
cp "$manifest" "$data_root/licenses/tools-manifest.json"
cp "$project_root/licenses/WRITTEN_SOURCE_OFFER.md" "$data_root/licenses/"

jq -r '
  "# Bundled third-party tools", "",
  "| Tool | Version | License | Source | SHA-256 |",
  "| --- | --- | --- | --- | --- |",
  (.tools[] | "| \(.name) | \(.version) | \(.license) | \(.sourceUrl) | `\(.archive.sha256 // ("md5:" + .archive.md5))` |"),
  "", "OpenTools remains GPL-3.0-or-later. Third-party programs retain their own licenses."
' "$manifest" > "$data_root/THIRD_PARTY_TOOLS.md"

if [[ "$platform" == "macos" ]]; then
    signing_identity="${MACOS_SIGNING_IDENTITY:--}"
    if [[ "$signing_identity" == "-" ]]; then
        codesign --force --deep --sign - "$image_root"
    else
        codesign --force --deep --options runtime --timestamp \
            --sign "$signing_identity" "$image_root"
    fi
    codesign --verify --deep --strict "$image_root"
fi

for tool_order in $(jq -r '.tools[].id' "$manifest"); do
    tool_json="$(jq -c --arg id "$tool_order" '.tools[] | select(.id == $id)' "$manifest")"
    while IFS= read -r required; do
        path="$data_root/$(jq -r '.destination' <<<"$tool_json")/$required"
        file -L "$path" >> "$data_root/licenses/tools/$tool_order/NOTICE.txt"
    done < <(jq -r '.requiredFiles[]' <<<"$tool_json")
done

smoke_project="$work/smoke-project"
cli_version="$("$cli_launcher" --version)"
[[ "$cli_version" == *"OpenTools $version"* ]] || {
    printf 'Packaged CLI version mismatch: %s\n' "$cli_version" >&2
    exit 2
}
doctor_json="$("$cli_launcher" --project "$smoke_project" doctor --json)"
# MEME/MAST are optional external tools on Unix packages; a missing copy is
# surfaced in Settings rather than blocking an otherwise valid package.
if ! jq -e '.ok == true and ([.data.externalTools[] | select(.id != "annotation.meme" and .id != "annotation.mast") | .available] | all)' <<<"$doctor_json" >/dev/null; then
    printf 'Packaged external tool doctor check failed.\n%s\n' "$doctor_json" >&2
    exit 2
fi

if [[ "$platform" == "linux" ]]; then
    package_root="$work/$package_name"
    mv "$image_root" "$package_root"
    archive="$output_dir/$package_name.tar.gz"
    rm -f "$archive"
    tar --owner=0 --group=0 --numeric-owner -czf "$archive" -C "$work" "$package_name"
    required_entries=(
        "$package_name/bin/OpenTools"
        "$package_name/bin/OpenToolsCLI"
        "$package_name/lib/app/opentools.jar"
        "$package_name/lib/runtime/lib/modules"
        "$package_name/lib/tools/blast/bin/blastn"
        "$package_name/lib/tools/diamond/diamond"
        "$package_name/lib/tools/hmmer/bin/hmmscan"
        "$package_name/lib/tools/muscle/muscle"
        "$package_name/lib/tools/trimal/trimal"
        "$package_name/lib/tools/iqtree/bin/iqtree2"
        "$package_name/lib/tools/mcscanx/MCScanX"
    )
    entries="$(tar -tzf "$archive")"
else
    package_root="$work/$package_name"
    mkdir -p "$package_root"
    mv "$image_root" "$package_root/OpenTools.app"
    archive="$output_dir/$package_name.zip"
    rm -f "$archive"
    ditto -c -k --sequesterRsrc --keepParent "$package_root" "$archive"
    required_entries=(
        "$package_name/OpenTools.app/Contents/MacOS/OpenTools"
        "$package_name/OpenTools.app/Contents/MacOS/OpenToolsCLI"
        "$package_name/OpenTools.app/Contents/app/opentools.jar"
        "$package_name/OpenTools.app/Contents/runtime/Contents/Home/lib/modules"
        "$package_name/OpenTools.app/Contents/tools/blast/bin/blastn"
        "$package_name/OpenTools.app/Contents/tools/diamond/diamond"
        "$package_name/OpenTools.app/Contents/tools/hmmer/bin/hmmscan"
        "$package_name/OpenTools.app/Contents/tools/muscle/muscle"
        "$package_name/OpenTools.app/Contents/tools/trimal/trimal"
        "$package_name/OpenTools.app/Contents/tools/iqtree/bin/iqtree2"
        "$package_name/OpenTools.app/Contents/tools/mcscanx/MCScanX"
    )
    entries="$(unzip -Z1 "$archive")"
fi
for required in "${required_entries[@]}"; do
    grep -Fxq "$required" <<<"$entries" || {
        printf 'Packaged archive is missing required entry: %s\n' "$required" >&2
        exit 2
    }
done

archive_hash="$(hash_file "$archive" | awk '{print toupper($1)}')"
archive_bytes="$(wc -c < "$archive" | tr -d ' ')"
jq -n \
    --arg package "$(basename "$archive")" \
    --arg sha256 "$archive_hash" \
    --argjson bytes "$archive_bytes" \
    --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg javaVersion "$java_version" \
    --slurpfile manifest "$manifest" \
    '{
      package: $package,
      sha256: $sha256,
      bytes: $bytes,
      builtAt: $builtAt,
      javaVersion: $javaVersion,
      toolsManifest: "licenses/tools-manifest.json",
      tools: ($manifest[0].tools | map({
        Tool: .name,
        Version: .version,
        License: .license,
        SHA256: .archive.sha256,
        MD5: .archive.md5,
        Source: .sourceUrl
      }))
    }' > "$archive.sha256.json"

if [[ "$keep_work" -eq 0 ]]; then
    rm -rf "$work"
fi

printf '%s\n%s\n' "$archive" "$archive_hash"
