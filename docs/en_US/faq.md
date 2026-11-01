# FAQ

## Why does the portable package need the full directory?

`OpenTools.exe` is a launcher. It needs `runtime/`, `app/`, and `tools/` from the
same package. Copying the EXE alone is not supported.

## Is Java or conda required?

The Windows portable package needs neither. The general JAR requires Java 21.
Neither package requires conda.

## Why is an external tool shown as missing?

Run Environment Check in Settings. In the portable package, confirm `tools/` was
not removed. Developer builds can set the corresponding `OPENTOOLS_*` variable,
choose an explicit task path, or add a compatible executable to `PATH`.

## Why is the first BLAST run slower?

OpenTools builds a reusable database keyed by subject hash, BLAST version,
program, and database type. Later matching runs reuse the cache within configured
size and entry limits.

## Why does the output VCF have no index?

Indexes require a `##contig` dictionary and dictionary-ordered records.
Compressed VCF uses TBI, BCF uses CSI, and plain uncompressed VCF is not indexed.

## Why does CRAM require a reference?

CRAM may store differences from a reference. A reference is required when it is
not embedded in the file.

## Why does AUTO still use one worker?

`threads=0` is conservative AUTO. Small files or sequential I/O may resolve to
one worker. `threads=1` forces the sequential path.

## Does Docs need a network connection?

No. Markdown, screenshots, and other resources are packaged. Raw HTML, scripts,
and remote images are not loaded. HTTP(S) links open in the system browser.

## Is a plugin a security sandbox?

No. Plugins are disabled by default, but an enabled Java plugin can use the
current user's permissions. Only enable trusted plugins.

## What should a bug report contain?

Include the OpenTools version, task ID, OS, error code, minimal reproducible
input, and sanitized `manifest.json` or `run.log`. Do not include secrets or
restricted research data.
