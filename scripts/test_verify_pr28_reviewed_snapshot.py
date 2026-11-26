#!/usr/bin/env python3
"""Tests for the PR #28 review-scope probe using synthetic git histories.

Each case builds a tiny throwaway repository so the probe's pass/fail
behaviour can be exercised without touching project history or the network.
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

PROBE = Path(__file__).resolve().parent / "verify_pr28_reviewed_snapshot.py"
ENV = os.environ.copy()
ENV.update({
    "GIT_AUTHOR_NAME": "probe",
    "GIT_AUTHOR_EMAIL": "probe@example.com",
    "GIT_COMMITTER_NAME": "probe",
    "GIT_COMMITTER_EMAIL": "probe@example.com",
    "GIT_AUTHOR_DATE": "2020-01-01T00:00:00Z",
    "GIT_COMMITTER_DATE": "2020-01-01T00:00:00Z",
})

failures = []


def git(repo, *args, allow_fail=False):
    proc = subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True, text=True, env=ENV, check=False,
    )
    if proc.returncode and not allow_fail:
        raise RuntimeError(f"git {' '.join(args)} failed: {proc.stderr}")
    return proc


def write(repo, files):
    for path, content in files.items():
        target = repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)


def commit(repo, message):
    git(repo, "add", "-A")
    git(repo, "commit", "-qm", message)
    return git(repo, "rev-parse", "HEAD").stdout.strip()


def repo_at(tmp, name):
    path = tmp / name
    path.mkdir(parents=True)
    git(path, "init", "-q", "-b", "main")
    git(path, "config", "user.name", "probe")
    git(path, "config", "user.email", "probe@example.com")
    return path


def probe(repo, snapshot, upstream, head, published):
    return subprocess.run(
        [sys.executable, str(PROBE),
         "--snapshot", snapshot,
         "--integration-parent", upstream,
         "--revised-head", head,
         "--published", published],
        cwd=repo, capture_output=True, text=True, check=False,
    )


def check(name, condition, detail=""):
    print(("PASS" if condition else "FAIL") + f": {name}")
    if not condition:
        if detail:
            print(f"     {detail}")
        failures.append(name)


def clean_history(tmp, name):
    """Base -> reviewed snapshot (pr.txt) + upstream (up.txt) -> clean merge."""
    repo = repo_at(tmp, name)
    write(repo, {"base.txt": "base\n"})
    base = commit(repo, "base")

    git(repo, "checkout", "-qb", "pr", base)
    write(repo, {"pr.txt": "reviewed\n"})
    snapshot = commit(repo, "reviewed snapshot")

    git(repo, "checkout", "-q", "-B", "main", base)
    write(repo, {"up.txt": "upstream\n"})
    upstream = commit(repo, "upstream work")

    git(repo, "checkout", "-q", "pr")
    git(repo, "merge", "-q", "--no-ff", "-m", "integrate upstream", "main")
    head = git(repo, "rev-parse", "HEAD").stdout.strip()
    return repo, snapshot, upstream, head


def merged_with_extra_files(repo, files):
    """Complete a pending merge after writing extra/changed files into the index."""
    write(repo, files)
    git(repo, "add", "-A")
    git(repo, "commit", "--no-edit", "-qm", "integrate upstream")
    return git(repo, "rev-parse", "HEAD").stdout.strip()


def conflicting_history(tmp, name):
    """Base -> snapshot and upstream change the same line; merge must resolve."""
    repo = repo_at(tmp, name)
    write(repo, {"shared.txt": "one\ntwo\nthree\n"})
    base = commit(repo, "base")

    git(repo, "checkout", "-qb", "pr", base)
    write(repo, {"shared.txt": "one\nSNAPSHOT\nthree\n"})
    snapshot = commit(repo, "reviewed snapshot")

    git(repo, "checkout", "-q", "-B", "main", base)
    write(repo, {"shared.txt": "one\nUPSTREAM\nthree\n"})
    upstream = commit(repo, "upstream work")

    git(repo, "checkout", "-q", "pr")
    result = git(repo, "merge", "--no-commit", "--no-ff", "main", allow_fail=True)
    check(f"{name}: test setup produced the expected conflict", result.returncode != 0)
    return repo, snapshot, upstream


def main():
    with tempfile.TemporaryDirectory(prefix="pr28-probe-") as tmp:
        tmp = Path(tmp)

        # 1. A clean merge with an unchanged snapshot passes.
        repo, snapshot, upstream, head = clean_history(tmp, "clean-pass")
        result = probe(repo, snapshot, upstream, head, head)
        check("clean reviewed merge is accepted", result.returncode == 0, result.stdout + result.stderr)
        check("clean run reports the verified scope", "Reviewed scope confirmed" in result.stdout)

        # 2. A publication with different content fails.
        base = git(repo, "merge-base", snapshot, upstream).stdout.strip()
        result = probe(repo, snapshot, upstream, head, base)
        check("published tree mismatch is rejected", result.returncode == 1)
        check("published mismatch names the failing check",
              "FAIL: published merge tree equals revised head tree" in result.stdout)

        # 3. An extra file added during the merge is beyond the snapshot.
        repo2, snap2, up2, _ = clean_history(tmp, "clean-extra")
        git(repo2, "reset", "-q", "--hard", snap2)
        git(repo2, "merge", "--no-commit", "--no-ff", up2, allow_fail=True)
        tampered = merged_with_extra_files(repo2, {"extra.txt": "not reviewed\n"})
        result = probe(repo2, snap2, up2, tampered, tampered)
        check("extra path beyond the snapshot is rejected", result.returncode == 1)
        check("extra path names the failing check",
              "FAIL: every path changed by the merge was changed by the upstream integration" in result.stdout)

        # 4. A non-merge head is rejected.
        result = probe(repo2, snap2, up2, snap2, snap2)
        check("non-merge head is rejected", result.returncode == 1)
        check("non-merge head names the failing check",
              "FAIL: revised head is a two-parent merge" in result.stdout)

        # 5. Editing a file that only upstream touched is rejected.
        repo3, snap3, up3, _ = clean_history(tmp, "clean-tampered")
        git(repo3, "reset", "-q", "--hard", snap3)
        git(repo3, "merge", "--no-commit", "--no-ff", up3, allow_fail=True)
        tampered = merged_with_extra_files(repo3, {"up.txt": "tampered after review\n"})
        result = probe(repo3, snap3, up3, tampered, tampered)
        check("tampered upstream-only file is rejected", result.returncode == 1)
        check("tampered upstream-only file names the failing check",
              "FAIL: files only upstream changed are taken verbatim from upstream" in result.stdout)

        # 6. Unresolved conflict markers in a resolved file are rejected.
        repo4, snap4, up4 = conflicting_history(tmp, "unresolved")
        unresolved = merged_with_extra_files(
            repo4,
            {"shared.txt": "<<<<<<< pr\none\nSNAPSHOT\nthree\n=======\none\nUPSTREAM\nthree\n>>>>>>> main\n"},
        )
        result = probe(repo4, snap4, up4, unresolved, unresolved)
        check("unresolved conflict markers are rejected", result.returncode == 1)
        check("conflict markers name the failing check",
              "FAIL: resolved files keep no conflict markers" in result.stdout)

        # 7. A legitimate union resolution of the conflict passes.
        repo5, snap5, up5 = conflicting_history(tmp, "resolved")
        resolved = merged_with_extra_files(
            repo5, {"shared.txt": "one\nSNAPSHOT\nUPSTREAM\nthree\n"},
        )
        result = probe(repo5, snap5, up5, resolved, resolved)
        check("legitimate conflict resolution is accepted", result.returncode == 0,
              result.stdout + result.stderr)

    print()
    print(f"{len(failures)} failure(s).")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
