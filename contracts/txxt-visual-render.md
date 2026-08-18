<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT — Visual render (DESIGN TEST task contract)

Scope: the operator's DESIGN TEST (2026-08-17): produce a **visual render** of the
TxxT app — what the conversation list and a message thread will actually look like
on the Pixel 9 — faithful to `docs/DESIGN.md` and the operator's `docs/FEATURES.md`
edits. Produce real image artifacts in the repo (a `renders/` directory), embed
them at the top of `README.md` so they display on GitHub, commit, and push
(fetch/rebase first — the remote may be ahead; never force). The renders are the
deliverable: they must communicate the design to a human at a glance.

Authoritative sources read this session: `docs/DESIGN.md`, `docs/FEATURES.md`,
`docs/PRIVACY.md`, `docs/INSPIRATION.md`, the brand token source
(`piercingxx-branding/tokens/colors.json`), the logomark source
(`piercingxx-branding/assets/logomark.svg`), and the brand fonts measured on disk
(`xx-vitals/app/src/main/res/font/space_mono.ttf` and
`xx-vitals/app/src/main/res/font/jetbrains_mono.ttf`).

## State of the tree (measured this session)

Measured on branch `main` (HEAD `03047a5`), working tree clean apart from the
untracked `contracts/`. The project is in the **design phase only** — there is no
Android project, no source code, no render artifact, and no README. Every
deliverable the goal names is **not started**; the goal is not stale.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Real image artifacts in a `renders/` directory | **Not started** | no `renders/` directory anywhere in the tree (tree root holds only `docs/`, `contracts/`, `.gitignore`) |
| Conversation-list render | **Not started** | no image artifacts exist; the design it must encode is at `docs/DESIGN.md:59` (conversation list) |
| Message-thread render | **Not started** | no image artifacts exist; the design it must encode is at `docs/DESIGN.md:65` (conversation thread) |
| Renders embedded at the top of `README.md` | **Not started** | no `README.md` exists anywhere in the tree (searched: only `contracts/TxxT.md` and `docs/INSPIRATION.md` mention "README" as prose, no file) |
| Commit | **Not started** | `contracts/` is untracked; no render commit exists |
| Push (fetch/rebase first — never force) | **Not started / blocked** | the sandbox shell cannot reach GitHub (measured this session: `git fetch origin` fails with `could not read Username for 'https://github.com'` — no credential helper; `gh auth status` reports `not logged into any GitHub hosts`). The push cannot be executed by this box |

**Gate report.** There is no runnable gate in this tree this session: no Android
project, no test suite, no render script, no README — nothing to execute, so **no
real numbers were produced**. The measured state is **0 build files, 0 source
files, 0 tests, 0 render artifacts, 0 runnable gates**.

**Tooling measured present and runnable on this box (this session):**
- **PIL 12.3.0** is importable (`python3 -c "import PIL"` → `PIL 12.3.0`), so the
  renders can be drawn with PIL primitives. No `cairosvg`, no ImageMagick, no
  Inkscape, no `rsvg-convert` — the render must be drawn with PIL, not converted
  from SVG.
- **The brand fonts are on disk and readable** at
  `/media/Working-Storage/GitHub/Skippy-Project/xx-vitals/app/src/main/res/font/space_mono.ttf`
  and `.../jetbrains_mono.ttf` (measured: both files exist). PIL's
  `ImageFont.truetype` can load them. The render scripts must load these exact
  fonts so the renders carry the brand's monospace identity.
- **Pixel 9 display geometry** (verified this session against Google's spec):
  1080×2424 OLED at 422 PPI, 6.3-inch, 20:9. The renders are drawn at 1080×2424
  to be faithful to the device.

**Brand tokens the renders must carry** (from `piercingxx-branding/tokens/colors.json`,
read this session): `ink` `#000000` ground, `signal` `#FFFFFF` accent, opacity
stops `1A`/`40`/`80`/`CC`/`E6`/`FF` white-on-black, `graphite` `#131316`, `slate`
`#18181B`. The logomark geometry (from `assets/logomark.svg`, read this session):
two Signal-white X's at ±45° stacked with centers 135px apart, underline bar at
y=402 h=44, on an Ink rounded-square tile.

## Deferred verification (the box cannot prove these)

- **The push.** The goal names "commit, and push (fetch/rebase first — the remote
  may be ahead; never force)". The **commit is verifiable** (T4). The
  **fetch/rebase/push is not executable by this box**: measured this session,
  `git fetch origin` fails with `could not read Username for 'https://github.com'`
  (no credential helper in the sandbox shell) and `gh auth status` reports the CLI
  is not logged into any GitHub host. The remote `origin/main` is **ahead 1,
  behind 1** of local `main` (measured: `git branch -vv`), so a fetch/rebase is
  genuinely required before push and the box cannot perform it. Until the operator
  pushes (or grants the box network/credential access), the push claim stays
  **unverified**. The box must not invent a fake-passing push check; the local
  precondition (clean commit) is verified, the network push is the operator's.
- **Human visual fidelity.** Whether the renders "communicate the design to a
  human at a glance" is a human judgment the box cannot make. The box verifies the
  renders exist, are at Pixel 9 resolution, carry the brand ground/accent, and
  encode the design constraints (text-first, no bubbles, monospace, compose bar,
  no voice affordance); whether they *read* well to a human is the operator's call.
- **GitHub rendering.** Whether the embedded images actually display on GitHub
  requires the push to land. The box verifies the `README.md` markdown image
  references are relative and point at real files; the rendered result on GitHub
  is unverifiable until the push happens.

## Tasks

### T1 — Render the conversation list

Draw `renders/conversation-list.png` at 1080×2424 with PIL, faithful to
`docs/DESIGN.md:59` (conversation list) and the brand tokens in `docs/INSPIRATION.md:24`.
The screen shows text-first rows on AMOLED black (`ink` `#000000`): contact name
in Space Mono, a preview line (JetBrains Mono), and a tabular-figure timestamp in
Space Mono; no avatar tiles by default; unread count as a `signal`-white glyph,
not a red dot; the one-accent rule holds (monochrome plus the single `signal`
accent). The render script loads the brand fonts from the measured paths
(`xx-vitals/app/src/main/res/font/space_mono.ttf` and `jetbrains_mono.ttf`) and
draws a realistic set of rows (a starred contact, an unread thread, a blocked/
quarantined thread) so the design reads at a glance. The verify asserts the PNG
exists, is 1080×2424, and carries both the ink-black ground and the signal-white
accent — proving the render was produced and is faithful to the brand ground/
accent pair.

- verify: python3 scripts/verify_conversation_list.py
- files: scripts/render_conversation_list.py, scripts/verify_conversation_list.py, renders/conversation-list.png

### T2 — Render the message thread

Draw `renders/thread.png` at 1080×2424 with PIL, faithful to `docs/DESIGN.md:65`
(conversation thread) and the hard constraint in `docs/PRIVACY.md:41` (nothing
ever bubbles). The screen shows **text-first message lines on AMOLED black — no
chat bubbles, no rounded cards**: inbound left, outbound right, minimal chrome;
timestamps in Space Mono; sent = `signal`-white text on black (inverted emphasis,
`docs/INSPIRATION.md:186`), received = `slate`/`graphite` text; a compose bar at
the bottom with a minimal attachment affordance and **no voice-message
affordance** (`docs/PRIVACY.md:88`). The verify asserts the PNG exists, is
1080×2424, carries the ink ground and signal accent, and is non-blank (contains
text pixels beyond the ground) — proving the thread was actually drawn.

- verify: python3 scripts/verify_thread.py
- files: scripts/render_thread.py, scripts/verify_thread.py, renders/thread.png

### T3 — Write README.md and embed the renders at the top

Create `README.md` at the repo root with the two renders embedded at the **top**
of the file (above any prose) using **relative** markdown image references
(`renders/conversation-list.png` and `renders/thread.png`) so GitHub renders them
in place, plus a short maker-register description of the app and the design
posture in the dry maker register voice (`docs/INSPIRATION.md:86`). The verify
asserts `README.md` exists and its two image references appear near the top and
point at relative paths that resolve to real files.

- verify: python3 scripts/verify_readme.py
- files: README.md, scripts/verify_readme.py

### T4 — Commit the render deliverables

Stage and commit the render artifacts, the render/verify scripts, and `README.md`
as a single commit (message in the dry maker register voice, e.g. referencing the
visual render of the conversation list and thread). The verify is a script that
asserts the latest commit exists, contains the render PNGs and `README.md`, and
that the working tree is clean (no uncommitted changes to tracked files) — the
local precondition for a push. The verify runs `git` locally only; it does not
touch the network.

- verify: python3 scripts/verify_commit.py
- files: scripts/verify_commit.py

## Final gate

The whole-workstream gate is a single script that checks the deliverables hold
together — all renders exist at Pixel 9 resolution, `README.md` embeds them via
relative paths, and the latest commit contains them:

- python3 scripts/verify_render_deliverables.py

It must exit 0. No individual task claims this command as its verify; it is the
whole-workstream gate that closes the DESIGN TEST. The push remains deferred (see
Deferred verification) because the sandbox shell cannot reach GitHub this session.