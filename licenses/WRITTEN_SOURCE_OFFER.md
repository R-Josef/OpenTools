# Third-party source information

OpenTools bundles independent third-party programs. They are not relicensed under
OpenTools' GPL-3.0-or-later license.

Exact tool versions, official binary/source URLs, SHA-256 values, build commands,
license identifiers, and license locations are recorded in
`packaging/windows/tools.json`. A copy of that manifest is included in every
Windows portable package as `licenses/tools-manifest.json`.

For source-built HMMER and MCScanX binaries, the exact upstream source archive
used by the package build is included under `licenses/sources/`. HMMER and
MCScanX build commands and compiler flags are copied to their respective tool
license directories.

For GPL-licensed binary programs, the package includes the full upstream license,
the exact upstream version and an unmodified official source archive URL. The
build maintains an offline cache of every downloaded archive and records its
SHA-256 before packaging.

Anyone redistributing this package is responsible for preserving these notices
and for satisfying the applicable source-code distribution obligations. The
OpenTools project does not provide a legal warranty. Release maintainers must
perform a manual legal review whenever a tool version or build recipe changes.

This file is not an offer to sell software or support. It documents the source
locations associated with this exact package build.
