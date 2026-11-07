# Third-party source information

OpenTools bundles independent third-party programs. They are not relicensed
under OpenTools' GPL-3.0-or-later license.

The portable archives do not duplicate large upstream source archives. Exact
versions, source URLs, SHA-256 values, build recipes, patches, and runtime
requirements are recorded in `licenses/tools-manifest.json` and
`licenses/SOURCE_INFO.md`.

Each tagged release publishes
`opentools-<version>-corresponding-sources.tar.gz` in the same release. The
source archive contains:

- the exact OpenTools source revision;
- GPL-licensed tool sources;
- the exact Cygwin and MSYS2 runtime sources used by the Windows build;
- the MEME Suite source and the patch used for the Windows build;
- `sources.json` records and the applicable license texts.

If a portable archive is redistributed without its matching source archive,
the redistributor must provide equivalent access to the corresponding source
at no further charge. A version-specific upstream URL may be used when it is
stable, exact, and unmodified; otherwise the corresponding source must be
provided directly.

HMMER and MCScanX use BSD licenses that do not require source distribution.
Their exact upstream source URLs remain recorded in the tool manifest for
auditability.

MEME Suite uses the Regents of the University of California academic and
non-profit license. Its complete copyright and permission notice must remain
with every copy. Commercial use or incorporation into a commercial product
requires permission from UCSD. The OpenTools patch does not change those terms.

Microsoft Visual C++ Redistributable is a system prerequisite. OpenTools does
not bundle `vc_redist.x64.exe` or add tool-local copies of Microsoft runtime
DLLs. The private JDK runtime may contain Microsoft runtime components governed
by the JDK legal files under `runtime/legal`. Users may install the official
package from
<https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist>.

This file is not legal advice and does not relicense any third-party component.
Release maintainers must review licenses and source-availability details when
a tool, runtime, patch, or build recipe changes.
