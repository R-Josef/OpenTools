#!/usr/bin/env bash
set -euo pipefail

output_dir="${1:?Usage: build-unix-dependencies.sh OUTPUT_DIR}"
job_count="${OPENTOOLS_BUILD_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"
platform="$(uname -s)"

mkdir -p "$output_dir"

if [[ -z "${HMMER_SOURCE_DIR:-}" || -z "${TRIMAL_SOURCE_DIR:-}" || -z "${MCSCANX_SOURCE_DIR:-}" ]]; then
    printf 'HMMER_SOURCE_DIR, TRIMAL_SOURCE_DIR, and MCSCANX_SOURCE_DIR are required.\n' >&2
    exit 2
fi

(
    cd "$HMMER_SOURCE_DIR"
    if [[ ! -x src/hmmsearch ]]; then
        CFLAGS="${CFLAGS:--O2}" ./configure
        make -j"$job_count"
    fi
)
for program in hmmscan hmmsearch hmmpress hmmbuild; do
    cp -p "$HMMER_SOURCE_DIR/src/$program" "$output_dir/$program"
done

(
    cd "$MCSCANX_SOURCE_DIR"
    cxx_flags=(-DNDEBUG -U_GLIBCXX_ASSERTIONS -O2)
    c++ "${cxx_flags[@]}" struct.cc mcscan.cc read_data.cc out_utils.cc dagchainer.cc \
        msa.cc permutation.cc -o MCScanX
    c++ "${cxx_flags[@]}" struct.cc mcscan_h.cc read_homology.cc out_homology.cc \
        dagchainer.cc msa.cc permutation.cc -o MCScanX_h
    c++ "${cxx_flags[@]}" struct.cc dup_classifier.cc read_data.cc out_utils.cc \
        dagchainer.cc cls.cc permutation.cc -o duplicate_gene_classifier
)
for program in MCScanX MCScanX_h duplicate_gene_classifier; do
    cp -p "$MCSCANX_SOURCE_DIR/$program" "$output_dir/$program"
done

(
    cd "$TRIMAL_SOURCE_DIR"
    make clean
    make -j"$job_count" CC=c++ CXX=c++
)
for program in trimal readal statal; do
    cp -p "$TRIMAL_SOURCE_DIR/$program" "$output_dir/$program"
done

chmod 0755 "$output_dir"/*
{
    printf 'buildDate=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'platform=%s\n' "$platform"
    printf 'architecture=%s\n' "$(uname -m)"
    printf 'compiler=%s\n' "$(c++ --version | head -n 1)"
    printf 'hmmerVersion=3.4\n'
    printf 'mcscanVersion=1.0.0\n'
    printf 'trimalVersion=1.5.1\n'
    printf 'jobs=%s\n' "$job_count"
} > "$output_dir/BUILD-INFO.txt"
