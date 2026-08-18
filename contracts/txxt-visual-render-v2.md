<!-- nagatha: UNREVIEWED — no reviewer configured (SKIPPY_REVIEW_URL unset) -->

# TxxT — Visual render REDO v2 (task contract)

Scope: the operator's RENDER REDO v2 (2026-08-18, third arming; the harness walls
are fixed in 1.166). Produce the **visual renders** of the TxxT app — the
conversation list and a message thread — **through the design-render SKILL**
(`services/coding-agent/skills/design-render/` in the harness repo; read its
SKILL.md and follow its flow exactly). The mockups are written as **HTML/CSS**
with the fonts include; each is rendered via the **deployed pipeline** at
`~/.skippy/app/tools/` (`render_page.py`, `check_render_craft.py`,
`frames/fonts.css`) — those tools are **never copied into this repo**. The renders
must read as a modern app on a modern phone, faithful to `docs/DESIGN.md` (AMOLED,
Space Mono, text-first) and the operator's `docs/FEATURES.md` decisions. The new
PNGs are embedded at the very top of `README.md` with alt text. PNGs are verified
with **stdlib checks only** (file size + magic bytes — no PIL).

The v1 drafts are **deleted and superseded**; the old contract
`contracts/txxt-visual-render.md` (which drew with PIL) is obsolete and is not
referenced as an authority. This contract supersedes it.

Authoritative sources read this session: `docs/DESIGN.md`, `docs/FEATURES.md`,
`docs/PRIVACY.md`, `docs/INSPIRATION.md`, the design-render SKILL.md, and the
deployed pipeline source (`~/.skippy/app/tools/render_page.py`,
`~/.skippy/app/tools/check_render_craft.py`, `~/.skippy/app/tools/frames/fonts.css`,
`~/.skippy/app/tools/frames/pixel.html`,
`~/.skippy/app/services/coding-agent/runtime/design_critic.py`).

## State of the tree (measured this session)

Measured on branch `main` (HEAD `3d24da9`), up to date with `origin/main`, working
tree clean apart from the untracked `contracts/`. The project is still in the
**design phase** — there is no Android project, no source code, and **no render
artifact, no README, and no `scripts/` on `main`**. The goal is **not stale** for
every deliverable it names: nothing the v2 goal produces already exists on `main`.

| Deliverable the goal names | Status | Evidence (this session) |
|---|---|---|
| Conversation-list mockup (HTML/CSS) | **Not started** | no `mockups/` dir anywhere in the tree (tree root holds only `docs/`, `contracts/`, `.gitignore`); design to encode is `docs/DESIGN.md:59` |
| Message-thread mockup (HTML/CSS) | **Not started** | no `mockups/`; design to encode is `docs/DESIGN.md:65` |
| Rendered PNGs in `renders/` | **Not started** | no `renders/` dir on `main`; the only renders that exist are on the unrelated branch `keep/txxt-visual-render-v2-progress` (HEAD `c3c39e6`), which is **not** `main` and is not part of this workstream |
| `README.md` with PNGs embedded at the top | **Not started** | no `README.md` exists anywhere in the tree (searched: only prose mentions in `contracts/` and `docs/`; no file) |
| Stdlib PNG verify scripts | **Not started** | no `scripts/` dir exists |
| Commit of the render deliverables | **Not started** | `contracts/` is untracked; no render commit exists on `main` |

**Gate report.** There is no runnable gate in this tree this session: no test
suite, no render script, no README, no mockup — nothing to execute, so **no real
numbers were produced**. Measured state: **0 mockups, 0 renders, 0 scripts, 0
tests, 0 runnable gates**.

**Deployed pipeline (measured this session, on disk and reachable):**
- **Renderer** `~/.skippy/app/tools/render_page.py` exists and its CLI works
  (`--html`, `--out`, `--frame pixel`, `--viewport`, `--dpr`). It serves the
  mockup page and the frame's fonts from the harness repo root, so a mockup in
  this repo must reference the deployed fonts by the **absolute** path
  `/tools/frames/fonts.css` (render_page's handler serves `/tools/frames/*` and
  `/assets/fonts/*` from `~/.skippy/app`, verified by reading its source this
  session). The `--frame pixel` template (`frames/pixel.html`) renders a
  flagship-phone bezel with a 1080×2424-class screen by default.
- **Craft lint** `~/.skippy/app/tools/check_render_craft.py` runs with plain
  `python3` (pure stdlib). Measured: `python3 ~/.skippy/app/tools/check_render_craft.py
  <file>` executes and returns exit 1 on HIGH findings (missing fonts include /
  single type size) and exit 0 when clean. It is **not** executable directly
  (mode `-rw-r--r--`), so it must be invoked via `python3`.
- **Vision critic** `~/.skippy/app/services/coding-agent/runtime/design_critic.py`
  is importable from the deployed venv: `~/.skippy/venv/bin/python3 -c "import
  sys; sys.path.insert(0,'/home/piercingxx/.skippy/app'); from
  services.coding_agent.runtime.design_critic import critique_render"` succeeds.
  The venv python has `httpx 0.28.1` (measured). The review endpoint
  `http://100.122.68.2:30001` is **reachable this session** (HTTP 200 on
  `/v1/models`), so the critique can run with its two-round budget
  (`iterate_render(..., max_rounds=2)`).
- **Chromium / playwright is NOT runnable in this sandbox shell.** Measured:
  `render_page.py` exits 2 with `chromium is unavailable: playwright package is
  not installed` under both the system `python3` and `~/.skippy/venv/bin/python3`.
  The render step could **not be smoke-tested this session**. The harness's own
  execution environment is expected to have the deployed renderer working (it is
  the harness's own tool), but that is **unverified on this box** — see Deferred
  verification.

**Brand tokens the renders must carry** (from `docs/INSPIRATION.md` §1 and
`docs/DESIGN.md` §"Brand system", read this session): AMOLED `ink` `#000000`
ground; `signal` `#FFFFFF` is the single reserved-white accent; body text tops
out at 90% white; the white-opacity ramp (`1A`/`40`/`80`/`CC`/`E6`/`FF`) carries
hierarchy, never hue. **One accent per screen.** Space Mono for
names/timestamps/unread counts; JetBrains Mono for message body. **Messages are
text-first lines, never chat bubbles** (`docs/PRIVACY.md:41` §2). **No voice
affordance** (`docs/PRIVACY.md:88` §5). Unread = `signal` glyph, not a red dot.

## Deferred verification (the box cannot prove these)

- **The render itself.** `render_page.py` could not be executed this session:
  playwright is not importable from the system `python3` or the deployed venv
  python (measured: both exit 2, "chromium is unavailable"). The mockups, craft
  lint, and the vision critique all verified present/runnable, but **no PNG was
  actually produced on this box this session**. T3/T4 depend on the harness's
  execution environment having the deployed renderer's Chromium backend working.
  Until a real render runs and a PNG is produced, the "renders exist at Pixel-9
  resolution" claim stays **unverified**.
- **Vision-critique endpoint at execution time.** The critique hits
  `http://100.122.68.2:30001` (reachable this session, HTTP 200), but that is a
  network dependency. If it is unreachable when the harness runs T5, the critic
  returns `ok: False`; the skill's fallback is to continue with the mechanical
  lint and say the vision pass did not run. Whether any HIGH finding is truly
  resolved is a visual judgment the critic makes, not the box.
- **Human visual fidelity.** Whether the renders "read as a modern app on a
  modern phone" is a human judgment the box cannot make. The box verifies the
  renders exist, are at Pixel-9 resolution, carry the brand ground/accent, pass
  the craft checklist, and encode the design constraints (text-first, no bubbles,
  no voice affordance, monospace, compose bar); whether they *read* well to a
  human is the operator's call.
- **GitHub rendering.** Whether the embedded images display correctly on GitHub
  requires a push to land. The box verifies the `README.md` image references are
  relative and resolve to real files; the rendered result on GitHub is
  unverifiable until a push happens. This contract does **not** scope a push (the
  v2 goal names only embed + verify; push remains the operator's, and the sandbox
  cannot reach GitHub over HTTPS — the `txxt-private-repos-blocked` fact).

## Tasks

### T1 — Write the conversation-list mockup

Write `mockups/conversation-list.html`, a standalone HTML/CSS mockup of the
conversation list faithful to `docs/DESIGN.md:59` and the brand system in
`docs/DESIGN.md:42` / `docs/INSPIRATION.md` §1. The page is the **screen**, not a
drawing of a phone: a 390×844 app viewport on the AMOLED `ink` `#000000` ground,
centered, with the device chrome left to the `--frame pixel` renderer. Content is
**text-first rows**: contact name in Space Mono, a preview line (JetBrains Mono),
a tabular-figure timestamp in Space Mono; no avatar tiles by default; unread count
as a `signal`-white glyph, not a red dot; the one-accent rule holds (monochrome
plus the single `signal` accent). Include a starred contact (call-through,
`docs/PRIVACY.md:105` §6) and an unread thread so the design reads at a glance.
Include the deployed fonts via the absolute path
`<link rel="stylesheet" href="/tools/frames/fonts.css">` (render_page serves
`/tools/frames/` from `~/.skippy/app`). Satisfy the craft checklist: spacing on
the 8px scale, 2–4 distinct font sizes, at most two font families (Space Mono +
JetBrains Mono — the brand is mono), body text ≥ 14px, styles in a `<style>`
block (no more than three inline `style=` attributes). The verify is the deployed
craft lint on this file; it must exit 0 (no HIGH findings).

- verify: python3 ~/.skippy/app/tools/check_render_craft.py mockups/conversation-list.html
- files: mockups/conversation-list.html

### T2 — Write the message-thread mockup

Write `mockups/thread.html`, a standalone HTML/CSS mockup of a conversation
thread faithful to `docs/DESIGN.md:65` and the hard constraint in
`docs/PRIVACY.md:41` §2 — **no chat bubbles, no rounded cards**: messages are
**text-first lines on AMOLED black**, inbound left, outbound right, minimal
chrome. Timestamps in Space Mono. Sent = `signal`-white text on black (inverted
emphasis, `docs/DESIGN.md:53`); received = `slate`/`graphite` text. A compose bar
at the bottom with a minimal attachment affordance and **no voice-message
affordance** (`docs/PRIVACY.md:88` §5). Same page contract as T1: 390×844 app
viewport on the ink ground, deployed fonts via the absolute
`/tools/frames/fonts.css` include, craft checklist satisfied (8px spacing, 2–4
sizes, ≤2 families, body ≥14px, styles in a `<style>` block). The verify is the
deployed craft lint on this file; it must exit 0.

- verify: python3 ~/.skippy/app/tools/check_render_craft.py mockups/thread.html
- files: mockups/thread.html

### T3 — Render the conversation list to PNG

Render `mockups/conversation-list.html` to `renders/conversation-list.png` via the
**deployed** renderer (never a copy): `~/.skippy/app/tools/render_page.py --html
mockups/conversation-list.html --frame pixel --out renders/conversation-list.png`.
`--frame pixel` wraps the page in the flagship-phone frame at the 1080×2424-class
screen, so the PNG reads as a phone. Before rendering, **delete any pre-existing
render** for this screen so no stale artifact survives. The verify is a **stdlib
only** script (no PIL) that asserts `renders/conversation-list.png` exists, its
first 8 bytes are the PNG magic signature, and its dimensions are the Pixel-9
screen size (1080×2424) — proving the render was actually produced and is a
phone-sized artifact.

- verify: python3 scripts/verify_render_conversation_list.py
- files: scripts/verify_render_conversation_list.py, renders/conversation-list.png

### T4 — Render the message thread to PNG

Render `mockups/thread.html` to `renders/thread.png` via the **deployed**
renderer: `~/.skippy/app/tools/render_page.py --html mockups/thread.html --frame
pixel --out renders/thread.png`. Before rendering, **delete any pre-existing
render** for this screen. The verify is a **stdlib only** script (no PIL) that
asserts `renders/thread.png` exists, its first 8 bytes are the PNG magic
signature, and its dimensions are 1080×2424 — proving the thread render was
produced and is a phone-sized artifact.

- verify: python3 scripts/verify_render_thread.py
- files: scripts/verify_render_thread.py, renders/thread.png

### T5 — Vision critique both renders within the two-round budget

Run the **deployed** vision critic on both rendered PNGs and apply its HIGH
findings, within the skill's two-round budget. The critic is importable from the
deployed venv (`~/.skippy/venv/bin/python3` with `sys.path` including
`~/.skippy/app`); the skill's `iterate_render(render_fn, critique_args,
max_rounds=2)` drives the loop — critique, hand HIGH findings to a re-render
pass, re-critique, stop on no-HIGH or after two critiques. Feed it the brand
document (`docs/DESIGN.md` + `docs/INSPIRATION.md` §1 + `docs/PRIVACY.md` §2) and
a checklist naming the load-bearing constraints (text-first, no bubbles, no voice
affordance, one `signal` accent, Space Mono / JetBrains Mono, 8px spacing). The
verify is a script run with the deployed venv python that imports the critic,
calls `critique_render` on both PNGs, and asserts `ok: True` on both — proving
the vision pass actually ran against the review endpoint (an `ok: False` reply,
e.g. an unreachable endpoint, fails the task, which is the honest outcome; the
skill's no-critic fallback is documented in Deferred verification, not silently
swallowed). The script must not invent a vision pass if the critic cannot run.

- verify: ~/.skippy/venv/bin/python3 scripts/verify_vision_critique.py
- files: scripts/verify_vision_critique.py

### T6 — Write README.md and embed the renders at the top

Create `README.md` at the repo root with the two renders embedded at the **very
top** of the file (above any prose) using **relative** markdown image references
(`renders/conversation-list.png` and `renders/thread.png`) and **alt text that
describes the screen**, not the file (e.g. "Conversation list on a Pixel 9 —
AMOLED ground, Space Mono, text-first rows"). Below the images, a short
maker-register description of the app and its design posture in the dry maker
register voice (`docs/INSPIRATION.md:86`). The verify is a **stdlib only** script
that asserts `README.md` exists, its two image references appear near the top
(before the first heading), each carries alt text, and each relative path
resolves to a real file.

- verify: python3 scripts/verify_readme.py
- files: README.md, scripts/verify_readme.py

### T7 — Commit the render deliverables

Stage and commit the render deliverables — the mockups, the render/verify
scripts, `README.md`, and the rendered PNGs — as a single commit (message in the
dry maker register voice, referencing the visual render of the conversation list
and thread). The verify is a script that asserts the latest commit exists,
contains the mockups, the render PNGs, `README.md`, and the verify scripts, and
that the working tree is clean (no uncommitted changes to tracked files) — the
local precondition for any later push. The verify runs `git` locally only; it
does not touch the network.

- verify: python3 scripts/verify_commit.py
- files: scripts/verify_commit.py

## Final gate

The whole-workstream gate is a single script that checks the deliverables hold
together — both mockups pass the deployed craft lint (no HIGH), both renders exist
at Pixel-9 resolution with valid PNG magic bytes, `README.md` embeds them via
relative paths near the top, and the latest commit contains them:

- python3 scripts/verify_render_deliverables.py

It must exit 0. No individual task claims this command as its verify; it is the
whole-workstream gate that closes RENDER REDO v2. The push remains deferred (see
Deferred verification) because the v2 goal names embed + verify only, and the
sandbox shell cannot reach GitHub over HTTPS.