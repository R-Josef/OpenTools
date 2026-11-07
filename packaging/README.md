# Packaging inputs

Files under `packaging/` are versioned build inputs. Generated packages belong
under Maven's `target/` directory and must not be stored here.

- `packaging/windows/tools.json`: pinned Windows tool and runtime manifest.
- `packaging/linux/tools.json`: pinned Linux x64 tool manifest.
- `packaging/macos-x64/tools.json`: pinned macOS Intel tool manifest.
- `packaging/macos-arm64/tools.json`: pinned macOS Apple Silicon tool manifest.
- `packaging/sources.json`: source archives and build-runtime versions published
  in the shared corresponding-source archive.

Release archives are built by `.github/workflows/release.yml` on native
platform runners.
- `packaging/windows/README.txt`: readme copied into the portable package.
- `packaging/windows/opentools.ico`: Windows launcher icon.

Run `scripts/package-windows.ps1` to create the final ZIP under
`target/release/`.

Run `scripts/package-sources.sh` to create the shared
`opentools-<version>-corresponding-sources.tar.gz` and checksum JSON under
`target/release/`. Platform packages contain notices and build information, but
do not duplicate these source archives.
