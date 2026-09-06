#!/usr/bin/env python3
"""Reject JAR paths in every commit reachable from local refs and HEAD.

Fetch all remote heads/tags before running this check. It inspects Git tree
metadata only: no blob contents, secret scanning, dependency execution or build.
Exit codes: 0 = clean, 1 = forbidden paths, 2 = incomplete/failed inspection.
"""

import argparse
import os
import subprocess
import sys


# Exact exception: this user-owned plugin was deliberately retained in Kgui.
ALLOWED_JAR_PATHS = {
    "ELITEAYOTO/Kgui": frozenset({b"libs/Kchat-1.0.0-SNAPSHOT.jar"}),
}


def git(*arguments):
    """Read real history, ignoring replacement objects that could hide commits."""
    return subprocess.run(
        ["git", "--no-replace-objects", *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository",
        required=True,
        help="GitHub owner/repository; unknown repositories have no exceptions",
    )
    args = parser.parse_args(argv)
    try:
        if git("rev-parse", "--is-shallow-repository").strip() != b"false":
            print("Inspection refused: fetch complete history first.", file=sys.stderr)
            return 2

        # Inspect each distinct complete snapshot, including merge commits.
        # --all covers every local ref and HEAD, including fetched branches/tags.
        trees = set(
            git("rev-list", "--all", "--format=%T", "--no-commit-header").splitlines()
        )
        allowed = ALLOWED_JAR_PATHS.get(args.repository, frozenset())
        forbidden = set()
        for tree in sorted(trees):
            paths = git(
                "ls-tree", "-r", "--full-tree", "--name-only", "-z",
                tree.decode("ascii"),
            ).split(b"\0")
            forbidden.update(
                path for path in paths
                if path.lower().endswith(b".jar") and path not in allowed
            )
    except (OSError, subprocess.SubprocessError) as error:
        print(f"Git inspection failed: {error}", file=sys.stderr)
        return 2

    if forbidden:
        for path in sorted(forbidden):
            # repr keeps unusual filenames from injecting terminal control lines.
            print(f"Forbidden JAR path: {os.fsdecode(path)!r}", file=sys.stderr)
        print(
            "Repository hygiene failed: JARs remain in reachable history. "
            "Deleting a file in the latest commit is not sufficient.",
            file=sys.stderr,
        )
        return 1

    print(f"Repository hygiene passed: {len(trees)} distinct historical trees checked.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
