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

The Windows MEME/MAST runtime is built by `scripts/build-meme-cygwin.sh` from
MEME Suite 5.5.9 at
<https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz>, with the
expected SHA-256 recorded in that script. The bundled Cygwin runtime is covered
by <https://cygwin.com/licensing.html>; Cygwin source is available from
<https://cygwin.com/>. MEME's academic/non-profit license and Cygwin's LGPL
terms require separate legal review before redistribution.

Anyone redistributing this package is responsible for preserving these notices
and for satisfying the applicable source-code distribution obligations. The
OpenTools project does not provide a legal warranty. Release maintainers must
perform a manual legal review whenever a tool version or build recipe changes.

This file is not an offer to sell software or support. It documents the source
locations associated with this exact package build.
