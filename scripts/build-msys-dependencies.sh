#!/usr/bin/env bash
set -euo pipefail

tool_root="${1:-$PWD}"
output_dir="${2:-$tool_root/dependencies/native}"
job_count="${NUMBER_OF_PROCESSORS:-4}"

mkdir -p "$output_dir"

hmmer_dir="$tool_root/hmmer-3.4"
if [[ ! -x "$hmmer_dir/src/hmmsearch" ]]; then
    (
        cd "$hmmer_dir"
        CFLAGS="${CFLAGS:--O2}" ./configure
        make -j"$job_count"
    )
fi

for program in hmmscan hmmsearch hmmpress hmmbuild; do
    cp "$hmmer_dir/src/$program" "$output_dir/$program.exe"
done

mcscanx_dir="$tool_root/MCScanX-1.0.0"
(
    cd "$mcscanx_dir"
    cxx_flags=(-DNDEBUG -U_GLIBCXX_ASSERTIONS -O2)
    g++ "${cxx_flags[@]}" struct.cc mcscan.cc read_data.cc out_utils.cc dagchainer.cc msa.cc permutation.cc -o MCScanX
    g++ "${cxx_flags[@]}" struct.cc mcscan_h.cc read_homology.cc out_homology.cc dagchainer.cc msa.cc permutation.cc -o MCScanX_h
    g++ "${cxx_flags[@]}" struct.cc dup_classifier.cc read_data.cc out_utils.cc dagchainer.cc cls.cc permutation.cc -o duplicate_gene_classifier
)

for program in MCScanX MCScanX_h duplicate_gene_classifier; do
    cp "$mcscanx_dir/$program" "$output_dir/$program.exe"
done

trimal_dir="$tool_root/trimal-1.5.1/source"
(
    cd "$trimal_dir"
    make clean
    make -j"$job_count" CC=g++
)
for program in trimal readal statal; do
    cp "$trimal_dir/$program" "$output_dir/$program.exe"
done

for runtime in \
    msys-2.0.dll \
    msys-gcc_s-seh-1.dll \
    msys-stdc++-6.dll \
    libgcc_s_seh-1.dll \
    libstdc++-6.dll \
    libwinpthread-1.dll; do
    if [[ -f "/usr/bin/$runtime" ]]; then
        cp "/usr/bin/$runtime" "$output_dir/"
    fi
done

{
    printf 'buildDate=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'compiler=%s\n' "$(g++ --version | head -n 1)"
    printf 'hmmerVersion=3.4\n'
    printf 'hmmerCFLAGS=%s\n' "${CFLAGS:--O2}"
    printf 'hmmerCommand=CFLAGS=<flags> ./configure && make -j%s\n' "$job_count"
    printf 'mcscanVersion=1.0.0\n'
    printf 'mcscanCFLAGS=-DNDEBUG -U_GLIBCXX_ASSERTIONS -O2\n'
    printf 'trimalVersion=1.5.1\n'
    printf 'trimalCommand=make clean && make -j%s CC=g++\n' "$job_count"
    printf 'outputDirectory=%s\n' "$output_dir"
} > "$output_dir/BUILD-INFO.txt"

printf 'Built pinned HMMER and MCScanX sources into %s\n' "$output_dir"
