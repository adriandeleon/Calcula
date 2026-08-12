#!/usr/bin/env python3
"""Refuse to let a control byte reach a source file.

This exists because it happened: a NUL landed inside a Java string literal and sat in the repository
undetected, because `file` calls the result `data` and grep skips it as binary — so the file is
invisible to exactly the tool you would search with.

The guard it replaces was WORSE than nothing. It was `grep -qP '[\\x00-\\x08...]'`, and macOS ships
BSD grep, which has no -P: every invocation failed with "invalid option", returned non-zero, and the
`if` around it never fired. It printed "scan clean" for months without ever reading a byte.

So this is Python, checked into the repo and run by name, rather than a shell one-liner retyped from
memory each time.

Usage:  python3 scripts/check-text.py [paths...]     (default: every tracked text-ish file)
Exits non-zero, listing offenders.
"""
import pathlib
import subprocess
import sys

# Tab, newline and carriage return are the only control characters a source file may contain.
ALLOWED = {0x09, 0x0A, 0x0D}
SUFFIXES = {".java", ".css", ".md", ".xml", ".json", ".properties", ".sh", ".py", ".txt", ".yml", ".yaml"}


def tracked():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout
    return [pathlib.Path(line) for line in out.splitlines() if pathlib.Path(line).suffix in SUFFIXES]


def offenders(path):
    try:
        data = path.read_bytes()
    except OSError:
        return []
    return sorted({b for b in data if b < 0x20 and b not in ALLOWED} | ({0x7F} if 0x7F in data else set()))


def main(argv):
    paths = [pathlib.Path(a) for a in argv[1:]] or tracked()
    bad = False
    for path in paths:
        found = offenders(path)
        if found:
            bad = True
            print(f"{path}: control bytes {[hex(b) for b in found]}", file=sys.stderr)
    if bad:
        print("\ncontrol bytes found — a source file must be text", file=sys.stderr)
        return 1
    print(f"{len(paths)} files clean")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
