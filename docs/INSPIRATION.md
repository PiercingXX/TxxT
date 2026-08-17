# TxxT — Design Inspiration from PiercingXX Repos

What the three private repos contribute to TxxT's design. Read after `DESIGN.md`
(which holds the aesthetic direction) — this is the concrete brand-system and
technical-stack inheritance.

Sources read this session (paths on disk):

| Repo | Path | What it gives TxxT |
|---|---|---|
| `piercingxx-branding` | `/media/Working-Storage/GitHub/piercingxx-branding` | The brand system itself — colors, type, mark, voice, new-project checklist |
| `Nope-Mode` | `/media/Working-Storage/GitHub/Skippy-Project/Nope-Mode` | Cleanroom discipline, no-`INTERNET` privacy claim, Views+viewBinding stack, package layout, failure-mode table |
| `xx-vitals` | `/media/Working-Storage/GitHub/Skippy-Project/xx-vitals` | How the brand tokens reshape a real UI, one-accent rule, Compose stack, "X's layout, PiercingXX's skin" cleanroom approach |

---

## 1. The brand system TxxT inherits (from `piercingxx-branding`)

The one-line brief: **local-first tools with a spine. AMOLED black, pure white,
monospace. Blunt, dense, no fluff.**

### 1.1 Color — the exact tokens

| Token | Hex | Use |
|---|---|---|
| `ink` | `#000000` | Ground — AMOLED black, default background |
| `ink_raised` | `#09090B` | Cards / raised surfaces |
| `graphite` | `#131316` | Panels, secondary surfaces |
| `slate` | `#18181B` | Inputs, wells |
| `line` | `#1AFFFFFF` (10%) | Hairline borders, ring track |
| `shade` | `#40FFFFFF` (25%) | Disabled, "not tracked" |
| `muted` | `#80FFFFFF` (50%) | Secondary text |
| `strong` | `#CCFFFFFF` (80%) | Labels, glyphs |
| `text` | `#E6FFFFFF` (90%) | Body text — the ceiling for type |
| `signal` | `#FFFFFF` | **The accent — reserved** |

White-on-black opacity stops to reuse exactly: `1A` 10%, `40` 25%, `80` 50%,
`CC` 80%, `E6` 90%, `FF` 100%.

**Status colors** — glyph-first (`✓ ✗ ⚠ →`), colour only when it needs
attention: `ok` = 90% white, `info` = 50% white, `warn` = `#FDBA74`,
`error` = `#FF6767`.

**Rare colors** (`#A277FF` purple, `#61FFCA` green) — at most one instance per
screen, usually zero. Never UI chrome, never body text.

### 1.2 Two brand rules that reshape a UI (load-bearing for TxxT)

1. **The reserved-white rule.** Pure `#FFFFFF` belongs to Signal alone. Body
   text tops out at 90% white. If something is 100% white, it *is* the accent.
   Strong emphasis **inverts**: a Signal-white block with Ink-black text
   (buttons, active states, selection).
2. **The rule of one accent.** Monochrome plus *one* accent, never two. This
   deletes the conventional messaging palette (blue sent / grey received / red
   unread). The replacement is the opacity ramp — hierarchy from transparency,
   not hue.

### 1.3 Typography

Monospace **is** the identity. Both faces shipped in `res/font/`, never
system-dependent.

| Role | Face | Notes |
|---|---|---|
| Display / wordmark | **Space Mono** | Headlines, numerals, timestamps |
| Body / code / UI | **JetBrains Mono** | Long text, list rows, dense UI |
| System / dense lists | sans-serif-light | Where mono is too heavy |

Weight is light. Left-aligned, generous line-height, no centred paragraphs.
**Tabular figures throughout** — a message timestamp or unread count that
reflows as digits change is the tell of an unconsidered UI.

### 1.4 The mark

Underlined `XX` logomark — two white X's stacked, overlapping by a quarter, on
Ink, white bar beneath. On an Ink rounded-square tile for the app icon. Never
recolor to non-brand hue.

### 1.5 Voice

Two registers, one attitude (senior engineer who respects your time):

- **Product / in-app copy** — calm, factual, privacy-forward. *"Free and
  ad-free. Collects no personal data. Any data this feature uses never leaves
  your device."*
- **Maker / README / commits** — dry, opinionated. *"Defaults with a spine."*

Anti-slop rules: no throat-clearing, no intensifier crutches, no false
symmetry, no passive-voice deflection, code first, be dense.

### 1.6 Naming

New user-facing tools take the `Piercing` prefix or `PiercingXX` full name.
`TxxT` fits the `XX` flagship marker (like `XX-Vitals`, `XX-Stack`). Package
namespace `com.piercingxx.<app>`.

---

## 2. Cleanroom discipline (from `Nope-Mode`)

TxxT is a cleanroom equivalent of an existing SMS app (QKSMS / Silence /
Partisan). Same rule as Nope-Mode §1:

- **Read their docs, never their source.** Behavioral *ideas* are not
  copyrightable; expression is. Anyone extending TxxT must hold the same line.
- Study F-Droid listings, READMEs, published feature descriptions, and official
  Android API docs. Never open the source of the prior-art apps.
- The repo is **all rights reserved** (like the launcher, Nope-Mode, xx-vitals).

The "X's layout, PiercingXX's skin" approach from xx-vitals D5 is the
legally-cleanest and brand-correct answer: copying layout/IA is safe (idea);
copying palette/type/icons is not (expression).

---

## 3. The no-`INTERNET` privacy claim (from `Nope-Mode`)

Nope-Mode declares **no `INTERNET` permission** and that is a verifiable
privacy claim (`aapt2 dump permissions`). TxxT's feature list already says
fully offline — this is the same posture. Keep `INTERNET` out of the manifest;
it is the single strongest privacy statement an app can make and it is
machine-checkable.

---

## 4. Technical stack conventions (from both Android repos)

| Concern | Nope-Mode | xx-vitals | TxxT recommendation |
|---|---|---|---|
| Language | Kotlin | Kotlin | **Kotlin** (matches launcher) |
| UI toolkit | Views + viewBinding | Compose | **Views + viewBinding** (matches launcher + Nope-Mode; messaging is list-heavy, not animation-heavy) |
| Persistence | Room + Gson | Room + Postgres | **Room + Gson** (matches launcher backup JSON conventions) |
| Core logic | pure Kotlin, no `android.*` | pure Kotlin (`MetricMath`) | **Pure-Kotlin core** (JVM-testable without a device) |
| minSdk | 24 (Android 7) | 34 (Android 14) | **24+** (SMS works everywhere; no Health-Connect-style dependency) |
| Target | Pixel 9 Pro, GrapheneOS | Pixel 9 Pro, GrapheneOS | **Pixel 9, GrapheneOS, sideloaded APK** |

The shared architectural posture: **the logic that must be correct is pure
Kotlin with no Android imports**, so it is JVM-testable without a device. For
TxxT that is the SMS/MMS state machine, blocking filters, and backup serialization.

### 4.1 Package layout (pattern from Nope-Mode §13)

```
com.piercingxx.txxt
├── core/       pure-Kotlin: message state, blocking, backup model
├── data/       entities, DAOs, TxxTDatabase, BackupJson
├── service/    SmsReceiver, MmsReceiver, NotificationService
├── block/      BlockingFilter, keyword/phrase matching
└── ui/         ConversationListActivity, ThreadActivity, SettingsActivity
```

---

## 5. Failure-mode table (pattern from both repos)

Both repos ship a failure-mode table (§14 / §10). TxxT should too. Draft rows:

| Scenario | Required behaviour |
|---|---|
| SMS permission denied | Say so on first launch; never silently fail to send |
| Device reboot | Reconcile; unread counts and pending sends survive |
| Blocking filter matches a legit contact | Surface the block with a reason, allow override |
| MMS download fails | Retry with backoff; show the failed state, never a silent drop |
| Backup restore conflicts | Idempotent import; re-import is a no-op |
| Default SMS handler revoked | Warn loudly; the app is inert without the role |

---

## 6. What TxxT should NOT take from these repos

- **xx-vitals' Compose choice** — that app needed animated rings/charts; TxxT
  is list + bubbles, so Views matches the launcher and keeps the stack uniform.
- **xx-vitals' server/Postgres** — TxxT is fully offline (no `INTERNET`), so
  there is no server. Backup is local JSON, matching the launcher.
- **Nope-Mode's device-owner MDM role** — irrelevant to a messaging app.
- **`nope-mode/res/values/brand_colors.xml`** — it is **stale** (predates the
  current guide: carries lime `#9BE223`, `#111827`, and an `accent_*` set the
  guide removed). Do not copy tokens from it. Import from
  `piercingxx-branding/tokens/` instead.

---

## 7. Concrete design decisions this implies for TxxT

1. **Palette:** `ink` ground, `signal` white accent, opacity ramp for
   hierarchy. Sent = `signal` block with `ink` text (inverted emphasis);
   received = `slate`/`graphite` block. Unread = `signal` glyph, not a red dot.
2. **Type:** Space Mono for names/timestamps/unread counts; JetBrains Mono for
   message body. Tabular figures. Fonts shipped in `res/font/`.
3. **One accent per screen:** the compose "send" affordance, the active tab,
   the unread indicator — never two competing accents.
4. **No `INTERNET` permission:** verifiable privacy claim.
5. **Icon:** underlined-XX logomark on an Ink tile.
6. **Voice:** calm product register in-app, dry maker register in README/commits.