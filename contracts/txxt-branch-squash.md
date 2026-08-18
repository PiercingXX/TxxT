<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT Branch Squash — clean stale work branches so main is the only story on GitHub

Goal: **Clean this repo's stale work branches so `main` is the only story on
GitHub.** Delete ONLY branches matching `laundry-bot/*` or `keep/*` that are
EITHER already merged into `origin/main` OR belong to superseded/retired queue
items — after checking `~/skippy-queue/` (live item dirs) first and SKIPPING any
branch whose item name still has a live queue dir. Never touch `main` or any
non-matching branch; never force-push `main`. Finish by printing the surviving
branch list.

Origin: operator order BRANCH SQUASH (2026-08-18). Use `run_command` for the git
operations. If in doubt about a branch, KEEP it and say so.

---

## State of the tree (measured this session)

Measured 2026-08-18 on branch `main` (HEAD `3d24da9`, up to date with
`origin/main`). Working tree clean apart from the untracked `contracts/`. Remote
is `https://github.com/PiercingXX/TxxT.git`. There is **no test suite** in this
repo (design phase only — no Gradle project, no `tests/`, no `.venv`), so there
is no repo-native test command to run as a gate; the whole-workstream gate below
is a stdlib check script, matching the pattern of this repo's other contracts
(`contracts/txxt-visual-render-v2.md`).

### Branch inventory (measured this session)

`git branch -a` shows 5 local and 10 remote-tracking refs. The `laundry-bot/*`
and `keep/*` branches, mapped to their `~/skippy-queue/` items and that item's
live/retired state:

| Branch (ref) | Queue item | Queue state | Action |
|---|---|---|---|
| `origin/laundry-bot/queue-txxt-visual-render-i3` | visual-render (v1) | archived `txxt-visual-render-superseded-2026-08-18` (BRANCH file = this branch) | **delete** |
| `origin/laundry-bot/queue-txxt-visual-render-v2` | visual-render-v2 | **no queue dir** (neither live nor archived); contract `contracts/txxt-visual-render-v2.md` is the current active workstream | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i2` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i3` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i4` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i5` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i6` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i7` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i8` | visual-render-v2 | same (active v2) | **SKIP** |
| `origin/laundry-bot/queue-txxt-visual-render-v2-i9` | visual-render-v2 | same (active v2) | **SKIP** |
| `keep/txxt-visual-render-v2-progress` (local) | visual-render-v2 | active v2 (keep-progress branch) | **SKIP** |
| `laundry-bot/queue-txxt-visual-render-v2-i6` (local) | visual-render-v2 | active v2 (tracks origin `...-v2-i6`) | **SKIP** |
| `main` / `origin/main` | — | — | never touch |
| `skippy/session-20260817-2034` (local) | — | non-matching | never touch |
| `skippy/session-20260817-2237` (local) | — | non-matching | never touch |

### What already exists (already done — do not redo)

- **The queue's live/retired state is already discoverable.** `~/skippy-queue/`
  has live dirs `builds-on-valkyrie-ws3`, `builds-on-valkyrie-ws3-corrective`,
  `exit-plan-ws4`, `incident-*`, `INCIDENT-*`, and `contracts-store` — none of
  which map to a `laundry-bot/*`/`keep/*` branch in this repo. The only
  `txxt-visual-render*` queue artifact is the **archived** v1 item
  `~/skippy-queue/archive/txxt-visual-render-superseded-2026-08-18`, whose
  `SUPERSEDED` marker reads "The v2 item replaces this; old draft renders and
  branches are being deleted" and whose `BRANCH` file names
  `laundry-bot/queue-txxt-visual-render-i3`. The v2 item has **no queue dir** —
  it is tracked only by its contract, which is the current active workstream
  (`contracts/txxt-visual-render-v2.md`), so it is **not** superseded/retired.
- **No branch is merged into `origin/main`.** Measured with
  `git merge-base --is-ancestor <branch> origin/main`: every candidate branch
  returns `not-merged`. So the "already merged" half of the deletion criterion
  applies to none of them; the deletions rest entirely on the
  "belongs to a superseded/retired item" half.

### What does NOT exist (the gap this contract fills)

- **The retired v1 origin branch has not been deleted.** `origin/laundry-bot/queue-txxt-visual-render-i3`
  still exists. Deleting it (origin + any local copy) is the whole workstream
  here — it is the only branch that belongs to a superseded item.
- **No stale local branch exists to delete.** The only local `laundry-bot/*` and
  `keep/*` branches (`keep/txxt-visual-render-v2-progress`,
  `laundry-bot/queue-txxt-visual-render-v2-i6`) belong to the **active** v2 item
  and must be preserved. There is no local copy of the retired v1 branch.

### What the box cannot prove (deferred — see below)

- **The origin deletion itself.** This sandbox shell **cannot reach GitHub**:
  measured this session, `git fetch origin --prune` fails with `could not read
  Username for 'https://github.com': No such device or address` (no credential
  helper) and `gh auth status` reports "not logged into any GitHub hosts". So
  `git push origin --delete` cannot execute on this box. The deletion is scoped
  as a task whose verify asserts the branch is gone from `git branch -r`; if the
  push cannot run, that verify fails and the deletion stays **deferred** to the
  operator (or a network-enabled environment) — the box must not fake a passing
  push.
- **Merge-by-content.** None of the candidate branches is an ancestor of
  `origin/main`, so the "already merged" half of the criterion cannot be shown.
  The single deletion relies entirely on the superseded/retired half, proven by
  the archived v1 queue dir's `SUPERSEDED` + `BRANCH` markers.
- **GitHub's live remote state.** The box sees only its local remote-tracking
  refs. It cannot prove what GitHub's server shows until a fetch/push actually
  runs; if the box has no network, the inventory is taken from the existing
  local refs and the server state stays unverified.

---

## Tasks

### T1 — Fetch origin with prune and capture the branch inventory

Run `git fetch origin --prune` (operator step 1). If it fails because the box
has no network/credentials, **report that fetch failure** and continue using the
existing local remote-tracking refs — do not fake a fresh fetch. Then capture
the full branch inventory (operator step 2): write every local and origin branch
to `branch-inventory.txt` at the repo root (one branch per line, `git branch -a`
output), so the surviving list can be diffed against it at the end. Write
`tools/check_inventory.py`, a stdlib-only script that asserts
`branch-inventory.txt` exists and contains both `main` and `origin/main`.

- verify: python3 tools/check_inventory.py
- files: branch-inventory.txt, tools/check_inventory.py

### T2 — Delete the retired v1 origin branch

Delete the one branch that belongs to a superseded item —
`origin/laundry-bot/queue-txxt-visual-render-i3` (the archived v1 item's branch)
— on origin with `git push origin --delete laundry-bot/queue-txxt-visual-render-i3`.
**SKIP every `queue-txxt-visual-render-v2*` branch** — the v2 item is the current
active workstream (its contract is current; no superseded/retired marker, no
archive dir), and the task's rule is to keep any branch in doubt. Never
force-push `main`. If the push cannot run because the box has no network, report
it and leave the verify failing (the deletion is deferred to the operator — see
Deferred verification). Write `tools/check_origin_deleted.py`, a stdlib-only
script that asserts `origin/laundry-bot/queue-txxt-visual-render-i3` no longer
appears in `git branch -r`, while every `origin/laundry-bot/queue-txxt-visual-render-v2*`
branch still does.

- verify: python3 tools/check_origin_deleted.py
- files: tools/check_origin_deleted.py

### T3 — Confirm no local retired branch exists and the active local branches survive

There is no local `laundry-bot/*` or `keep/*` branch belonging to a retired
item — the only retired branch (`queue-txxt-visual-render-i3`) exists on origin
only. The two local `laundry-bot/*`/`keep/*` branches
(`keep/txxt-visual-render-v2-progress` and
`laundry-bot/queue-txxt-visual-render-v2-i6`) belong to the **active** v2 item
and must be preserved. This task asserts that preservation: no local
`laundry-bot/*` or `keep/*` branch was deleted, and both active local branches
still exist. Write `tools/check_local_kept.py`, a stdlib-only script that
asserts `keep/txxt-visual-render-v2-progress` and
`laundry-bot/queue-txxt-visual-render-v2-i6` both still appear in `git branch`,
and that no other `laundry-bot/*` or `keep/*` local branch was removed.

- verify: python3 tools/check_local_kept.py
- files: tools/check_local_kept.py

### T4 — Verify main and non-matching branches are untouched, print the surviving list

Confirm `main`, `origin/main`, both `skippy/session-*` local branches, and all
v2 branches were never deleted or force-pushed (the cleanup must not have
touched them), and that the active v2 branches were preserved. Then print the
surviving branch list (operator step 5) — every branch that still exists after
the cleanup, so the operator can see `main` plus the live/kept branches. Write
`tools/check_survivors.py`, a stdlib-only script that asserts `main`,
`origin/main`, both `skippy/session-*` local branches, all 9
`origin/laundry-bot/queue-txxt-visual-render-v2*` branches, and both active
local branches still exist, and prints the full surviving branch list to stdout.

- verify: python3 tools/check_survivors.py
- files: tools/check_survivors.py

---

## Deferred verification

The box cannot prove the following for this workstream; they stay unchecked and
belong to the operator or the live environment:

- **The origin deletion of `queue-txxt-visual-render-i3`.** This sandbox shell
  cannot reach GitHub (measured: `git fetch origin --prune` fails with no
  credential helper; `gh auth status` reports not logged in). `git push origin
  --delete` cannot run here, so the deletion is deferred to the operator or a
  network-enabled environment. The box must not fake a passing push; T2's verify
  will fail until the branch is actually gone from `git branch -r`.
- **Merge-by-content of the deleted branch.** The criterion's "already merged
  into origin/main" half is not satisfiable here — no candidate is an ancestor
  of `origin/main`. The single deletion is justified solely by the archived v1
  queue dir's `SUPERSEDED` + `BRANCH` markers. If the operator wants proof that
  the v1 branch's commits are preserved on `main` (not just that its item is
  retired), that is a manual review of the branch diff, not something the box
  asserts.
- **The v2 item's final disposition.** The v2 item has no queue dir (live or
  archived) and its contract is the current active render workstream, so its
  branches are kept. Whether that item is actually finished and its iteration
  branches (`-i2`..`-i9`) should later be cleaned is the operator's call.
- **GitHub's actual server state.** The box sees only local remote-tracking refs
  and what `git push origin --delete` reports. It cannot see the GitHub web UI or
  confirm a deletion propagated to other clones.

## Final gate

One command checks the whole workstream holds together — a single stdlib script
that asserts the complete end state: the retired v1 origin branch is gone, all
active v2 branches and both `skippy/session-*` branches survive, `main` and
`origin/main` are untouched, and the surviving branch list is printed:

python3 tools/check_branch_squash_gate.py

It must exit 0. No individual task above claims this command as its verify; each
task verifies a distinct artifact (the inventory file, the origin deletion, the
local preservation, the survivor list) with its own stdlib check script, so the
per-task checks are distinguishable, and this gate proves the cleanup left the
branch set in the intended state. The origin deletion itself remains deferred
(see Deferred verification) because this sandbox shell cannot reach GitHub.