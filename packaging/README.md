# Packaging inputs

Files under `packaging/` are versioned build inputs. Generated packages belong
under Maven's `target/` directory and must not be stored here.

- `packaging/windows/tools.json`: pinned Windows tool and runtime manifest.
- `packaging/windows/README.txt`: readme copied into the portable package.
- `packaging/windows/opentools.ico`: Windows launcher icon.

Run `scripts/package-windows.ps1` to create the final ZIP under
`target/release/`.
