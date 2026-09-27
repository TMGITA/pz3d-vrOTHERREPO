"""Reject absolute user-profile paths in tracked text or branch/tag history.

Run from the repository root. Ignored local evidence is intentionally not scanned.
"""
import argparse
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

PERSONAL_PATH = re.compile(
    r"(?i)(?:[a-z]:(?:\\+|/+)(?:users|documents and settings)(?:\\+|/+)"
    r"|/(?:home|Users)/)[^\s/\\`'\"]+"
)


def git(*args):
    return subprocess.check_output(['git', *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--history', action='store_true', help='scan all local branches and tags')
    args = parser.parse_args()
    failures = []
    checked = 0
    if args.history:
        blobs = {}
        for commit in git('rev-list', '--branches', '--tags').decode().splitlines():
            for entry in git('ls-tree', '-rz', commit).split(b'\0'):
                if not entry:
                    continue
                meta, name = entry.split(b'\t', 1)
                _, kind, oid = meta.split()
                if kind == b'blob':
                    blobs.setdefault(oid.decode(), name.decode())
        sources = ((name, git('cat-file', 'blob', oid)) for oid, name in blobs.items())
    else:
        names = git('ls-files', '-z').decode().split('\0')
        sources = ((name, Path(name).read_bytes()) for name in names if name and Path(name).is_file())
    for name, data in sources:
        checked += 1
        for number, line in enumerate(data.decode(errors='replace').splitlines(), 1):
            if PERSONAL_PATH.search(unquote(line)):
                failures.append(f'{name}:{number}')
    for location in failures:
        print(f'Personal absolute path: {location}')
    print(f'Checked {checked} files/blobs; {len(failures)} personal-path occurrences.')
    return int(bool(failures))


if __name__ == '__main__':
    raise SystemExit(main())
