#!/usr/bin/env python3
"""Regression probe for issue #32 (review scope of PR #28).

The independent review approved source snapshot 9524c73. The PR head was
revised afterwards to 8512cbc, which merges the then-current main
(8c98f308) into the reviewed snapshot, and PR #28 later merged as
dab9e8d7. This probe proves, with git plumbing only, that the revised
head introduces no content beyond that reviewed snapshot plus the intended
upstream integration:

1. the revised head is a two-parent merge whose first parent is the
   reviewed snapshot and whose second parent is the upstream tip;
2. every path changed between snapshot and head was already changed by
   the upstream integration; files the snapshot added are carried
   through verbatim, and files only upstream changed are taken verbatim;
3. the head's difference from Git's automatic three-way merge result is
   confined to the paths Git reports as conflicted, and none of those
   paths keeps unresolved conflict markers;
4. the published merge commit has exactly the revised head's tree, so
   nothing changed between the reviewed scope and the merge.

No JDK, Gradle, GPU, or network access is required.
"""
import argparse
import re
import subprocess
import sys

DEFAULT_SNAPSHOT = "9524c73a5c453cf9eb8cfa116ccbf5741ecea1a4"
DEFAULT_INTEGRATION_PARENT = "8c98f308724bc01cbdad48929494e3b60b4f0a43"
DEFAULT_REVISED_HEAD = "8512cbc85b20c98e357a0294d6c1d93b688a5bea"
DEFAULT_PUBLISHED_MERGE = "dab9e8d7718cf20941401e670d691248cd60e2a9"

STAGE_LINE = re.compile(r"^\d{6} [0-9a-f]{40} [123]\t(.*)$")

failures = []


def run_git(*args):
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )
    return proc


def check(name, condition, detail=""):
    print(("PASS" if condition else "FAIL") + f": {name}")
    if detail and not condition:
        print(f"     {detail}")
    if not condition:
        failures.append(name)
    return condition


def resolve(rev, label):
    proc = run_git("rev-parse", "--verify", f"{rev}^{{commit}}")
    commit = proc.stdout.strip()
    if not check(
        f"{label} `{rev}` resolves to a commit", bool(commit), proc.stderr.strip()
    ):
        sys.exit(1)
    return commit


def commit_parents(commit):
    return run_git("rev-list", "--parents", "-n", "1", commit).stdout.strip().split()[1:]


def commit_tree(commit):
    return run_git("rev-parse", f"{commit}^{{tree}}").stdout.strip()


def blob_of(commit, path):
    proc = run_git("rev-parse", "--verify", f"{commit}:{path}")
    return proc.stdout.strip() if proc.returncode == 0 else "<absent>"


def changed_paths(old, new):
    proc = run_git("diff", "--name-only", "-z", old, new)
    return set(p for p in proc.stdout.split("\0") if p)


def auto_merge(snapshot, integration_parent):
    """Return (tree, conflicted_paths) for Git's merge of the snapshot and parent.

    exit status is 1 when conflicts exist, which is expected input here,
    so parse the output regardless of status.
    """
    proc = run_git("merge-tree", "--write-tree", snapshot, integration_parent)
    lines = proc.stdout.splitlines()
    conflicted = set()
    tree = lines[0].strip() if lines else ""
    for line in lines[1:]:
        match = STAGE_LINE.match(line)
        if not match:
            break
        conflicted.add(match.group(1))
    if not check(
        "git merge-tree produced an automatic merge result",
        bool(tree),
        proc.stderr.strip(),
    ):
        sys.exit(1)
    return tree, conflicted


def blobs_have_conflict_markers(commit, paths):
    marked = []
    for path in paths:
        oid = blob_of(commit, path)
        if oid == "<absent>":
            continue
        proc = run_git("cat-file", "-p", oid)
        if re.search(r"(?m)^<<<<<<< ", proc.stdout):
            marked.append(f"{path} (has <<<<<<<)")
        if re.search(r"(?m)^>>>>>>> ", proc.stdout):
            marked.append(f"{path} (has >>>>>>>)")
    return marked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", default=DEFAULT_SNAPSHOT)
    parser.add_argument("--integration-parent", default=DEFAULT_INTEGRATION_PARENT)
    parser.add_argument("--revised-head", default=DEFAULT_REVISED_HEAD)
    parser.add_argument("--published", default=DEFAULT_PUBLISHED_MERGE)
    args = parser.parse_args()

    snapshot = resolve(args.snapshot, "reviewed snapshot")
    integration_parent = resolve(args.integration_parent, "upstream integration parent")
    head = resolve(args.revised_head, "revised head")
    published = resolve(args.published, "published merge")

    # 1. The revised head is a two-parent merge rooted in the reviewed snapshot.
    parents = commit_parents(head)
    check(
        "revised head is a two-parent merge",
        len(parents) == 2,
        f"parents: {parents}",
    )
    check(
        "first parent is the reviewed snapshot",
        len(parents) == 2 and parents[0] == snapshot,
        f"first parent: {parents[0] if parents else '<none>'}",
    )
    check(
        "second parent is the upstream integration parent",
        len(parents) == 2 and parents[1] == integration_parent,
        f"second parent: {parents[1] if len(parents) > 1 else '<none>'}",
    )

    # 4 (order: cheap first). The published merge is the same content as the head.
    check(
        "published merge tree equals revised head tree",
        commit_tree(published) == commit_tree(head),
        f"published={commit_tree(published)} head={commit_tree(head)}",
    )

    # 2. The merge changed nothing the upstream integration had not changed.
    base = run_git("merge-base", snapshot, integration_parent).stdout.strip()
    if not check("snapshot and integration parent share a merge base", bool(base)):
        sys.exit(1)

    snapshot_changed = changed_paths(base, snapshot)
    upstream_changed = changed_paths(base, integration_parent)
    head_changed = changed_paths(snapshot, head)

    extras = sorted(head_changed - upstream_changed)
    check(
        "every path changed by the merge was changed by the upstream integration",
        not extras,
        f"paths beyond upstream: {extras}",
    )

    upstream_only = sorted(upstream_changed - snapshot_changed)
    verbatim = True
    verbatim_detail = ""
    for path in upstream_only:
        if blob_of(head, path) != blob_of(integration_parent, path):
            verbatim = False
            verbatim_detail = f"first mismatch: {path}"
            break
    check(
        "files only upstream changed are taken verbatim from upstream",
        verbatim,
        verbatim_detail,
    )

    # 3. Conflict resolution is confined to conflicted paths with no markers left.
    auto_tree, conflicted = auto_merge(snapshot, integration_parent)
    resolution_paths = sorted(changed_paths(auto_tree, head) - conflicted)
    check(
        "head differs from the automatic merge only in conflicted paths",
        not resolution_paths,
        f"non-conflict differences: {resolution_paths}",
    )
    marked = blobs_have_conflict_markers(head, sorted(conflicted))
    check(
        "resolved files keep no conflict markers",
        not marked,
        f"marked: {marked}",
    )

    print()
    print(f"snapshot:           {snapshot}")
    print(f"integration parent: {integration_parent}")
    print(f"revised head:       {head}")
    print(f"automatic result:   {auto_tree}")
    print(f"published merge:    {published} (tree {commit_tree(published)})")
    if failures:
        print(f"\n{len(failures)} check(s) failed: the revised head may contain "
              "changes beyond the locally reviewed snapshot.")
        return 1
    print("\nReviewed scope confirmed: the revised head adds only the intended "
          "upstream integration to the locally reviewed snapshot.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
