# TxxT — Privacy & Notification Design

Design-phase spec for the privacy posture, notification behaviour, and
cross-app integration the operator asked to "dial in". Read after
`DESIGN.md` and `FEATURES.md`. This file is the authority for the privacy
defaults; DESIGN.md and FEATURES.md point to it.

> **Defaults with a spine.** Every item here is a *default*, not an option the
> user has to discover. Privacy leaks are opt-in, never opt-out.

---

## 1. Notification & receipt behaviour — off by default

The app ships with every "smart" messaging feature that leaks or announces
behaviour **disabled by default**. Nothing is enabled that the operator did
not explicitly turn on.

| Feature | Default | Notes |
|---|---|---|
| Read receipts | **OFF** | Not sent, not rendered. |
| Typing indicators | **OFF** | Not sent, not rendered. |
| Delivery reports | **OFF** | No `SMS_DELIVERY_REPORT` / read-report requests. |
| MMS "smart" features | **OFF** | No read/typing extensions, no delivery pings. Photos fetch on arrival. |
| RCS / Jibe | **OFF / excluded** | RCS is the vector for read receipts + typing indicators. Already recommended to skip (FEATURES.md Q5). Confirmed: **excluded**. |
| Notification bubbles / chat-heads | **OFF / never** | See §2. |
| Notification content preview | **OFF** | See §3. |
| MMS auto-download | **photos only** | Voice MMS is still dropped. See §8.1. |

**Protocol reality (be honest in the build):** plain SMS/MMS has **no** read
receipts or typing indicators at the protocol level — those are RCS/iMessage
features. So "blocking" them is mostly: (1) never requesting delivery/read
reports, (2) never rendering them if a proprietary channel smuggles one in,
and (3) **excluding RCS entirely**, which removes the whole class. The
load-bearing decision is the RCS exclusion, not per-message toggles.

### Channels and the version suffix

TxxT's sound channel (`txxt_messages_v3`) uses the **phone's default
notification sound**. Settings → Notification sound opens the system ringtone
picker; a custom pick mints `txxt_messages_vN` because Android freezes a
channel's sound at creation. Vibrate-only and silent channels sit alongside.

---

## 2. Nothing ever bubbles

**Confirmed interpretation (operator, 2026-08-18):** "nothing should ever
'bubble'" means **no message bubbles in the thread UI**. The conversation list
and thread are **text-first** — messages are rendered as plain text lines, not
in rounded chat-bubble cards. This is a hard design constraint, not a style
preference.

- **No chat-bubble rendering in the thread UI.** Messages are text lines with
  sender alignment (inbound left, outbound right), not bubble cards. This
  overrides any prior "hybrid layout with bubbles" phrasing in DESIGN.md /
  FEATURES.md (corrected in those files).
- **No Android notification bubbles / chat-heads** either — no
  `BUBBLE_DATA` / `FLAG_BUBBLE` on any notification, no floating window, no
  overlay permission.
- If the launcher or OS offers a bubble toggle, TxxT never opts in.

---

## 3. Notification content — redacted by default

- Notifications show **sender name only**, never message content, by default.
- Lock-screen privacy options (sender-only / content / nothing) default to
  **sender-only** (tightens FEATURES.md Q13 from "content" to "sender-only").
- No vibration/sound for contacts who are not starred (see §6).

---

## 4. Metadata scrubbing — automatic on every send

Any image or video leaving the device is scrubbed **before** it is attached.

- **EXIF / XMP / IPTC stripped** from images (GPS, camera make/model, timestamp,
  software, thumbnail residue).
- **Video metadata atoms stripped** (MP4/MOV metadata: GPS, device model,
  creation time, encoder).
- Scrub happens in the **pure-Kotlin core** (JVM-testable), so the scrub logic
  is unit-tested without a device.
- **No re-encode by default** — re-encoding changes quality and is expensive.
  Strip the metadata containers; offer a quality/format re-encode as an
  explicit per-send option if the operator wants it.
- The scrub is **not optional** — there is no "send with metadata" toggle.

---

## 5. Voice messages — never sent, never received

- **Send:** the voice-message affordance is **removed**. There is no way to
  record or attach a voice message. (Voice *calls* are unaffected — that is the
  dialer's job, not this app's.)
- **Receive:** an inbound MMS whose attachment is audio is **not downloaded and
  not stored**. It is dropped at the inbox boundary.
- **Sender feedback — protocol reality:** standard SMS/MMS has **no mechanism
  for a receiver to reject an inbound MMS and push an error back to the
  sender**; delivery is handled by the carrier's MMSC, and the sender will not
  get an automatic failure. The best achievable is an **automatic reply SMS**
  (off by default, per-contact overridable) reading *"Voice messages aren't
  accepted. Send text or a photo."* The "sender gets an error" requirement is
  therefore **partially met**: the voice message is never received/stored, and
  the sender is told — but not via a true protocol-level rejection. Flag this
  honestly rather than claim full delivery.

---

## 6. Starred contacts — always get through

The app knows who the operator's **starred** contacts are and treats them as
first-class.

- **Starred is a first-class contact flag** in the data model (persisted in
  Room, exported/imported with backup).
- **Call-through:** starred contacts' calls and texts **bypass every
  suppression** — blocking filters, keyword/unknown-sender blocking, quiet
  hours, notification redaction. Their messages always notify and their calls
  always ring.
- **Blocking overrides never apply to starred contacts**; a block rule that
  would match a starred contact is surfaced with a reason and does not apply
  silently (INSPIRATION.md §5 failure-mode table).
- **Starred is explicit, not inferred.** No "frequently contacted" heuristics
  that could promote a spammer to starred.
- Unstarred contacts keep the full suppression posture.

---

## 7. Theme auto-sync with the xx-launcher

TxxT's background theme follows the **xx-launcher**'s active theme automatically.

- **Contract:** the launcher publishes its active theme; TxxT subscribes. The
  named presets are the brand guide §3.3 set — AMOLED Night, Graphite, Forest
  Night, Ocean Drift, Burgundy, Paper, Mist.
- **Local only** — the sync is an on-device broadcast between two apps on the
  same handset; nothing about the theme is fetched from or published to a
  service.
- **Mechanism (confirmed and shipping):** the launcher broadcasts
  `xx.launcher.THEME_CHANGED` on theme change, carrying the theme's display name
  and its resolved background ARGB, targeted at each family app by package. All
  nine subscribe. TxxT's receiver — `.theme.ThemeSyncReceiver`, exported —
  resolves the carried name to a `ThemePreset`, persists it to the `txxt_theme`
  store so the choice survives process death, and the applier repaints.
- **Custom:** the launcher's Custom ground has no preset to resolve. TxxT keys
  off the *name* only and therefore stays on the last resolved preset when a
  Custom broadcast arrives; xx-dialer is the family app that consumes the raw
  ARGB off the broadcast.
- **User override:** a manual in-app theme still wins over auto-sync (explicit
  beats ambient). Pick a theme in Settings and the launcher stops overriding it.

---

## 8. Additional privacy improvements (proposed)

Beyond the operator's explicit list, these are worth adopting. Each is a
default-off or default-safe posture that costs little and leaks nothing.

1. **MMS photo fetch on arrival.** Inbound photos are retrieved when the
   notification-ind lands, then shown as `[Photo]` until the operator taps
   the row. Voice MMS is still dropped unstored. Empty/unknown PDUs are not
   invented as `[MMS]`. Holding the SMS role without a retrieve would swallow
   carrier MMS for the whole device.
2. **No link previews.** Never fetch a URL to render a preview — resolving it
   tells whoever hosts the link that the message was received and read. Links
   are plain text; tap to open in a browser.
3. **No contact avatars from network.** Avatars are local monograms or nothing.
   No reverse-lookup, no contact enrichment.
4. **No analytics / no crash reporting.** No analytics SDK, no crash reporter,
   no ads, no Play Services dependency. State it as fact, not aspiration.
5. **Screenshots and recents previews are allowed.** The operator captures
   the app; a black rectangle is not privacy.
6. **Biometric app lock** (optional, default off — but available) with a
   "hide content until unlocked" mode.
7. **Block unknown senders by default.** Messages from non-contacts go to a
   quarantine view, not the main thread list, unless the sender is starred.
8. **Per-contact notification control.** Silent / vibrate / sound / redacted
   per contact, defaulting to the global posture.
9. **Burn-after-read / auto-delete** (optional per-thread): delete messages
   after N days or on read. Default off.
10. **No clipboard auto-sync / no clipboard-history write** for message content.
11. **No message content in system recents or notification shade** (ties to §3).
12. **Scrub the image *before* it is added to the draft**, so the draft preview
    itself never holds metadata.

---

## 9. Open questions for the operator

1. **Voice-message sender feedback:** automatic reply SMS on by default or off?
   (Default proposed: off, per-contact overridable — an auto-reply could itself
   be unwanted.)
2. **Burn-after-read / auto-delete:** include? Default off proposed.
3. **Biometric lock:** include? Default off proposed.
4. **Theme-sync channel:** confirm the launcher's actual theme-publish mechanism
   once the launcher source is readable; the broadcast contract in §7 is a
   proposal until then.
5. **"Bubble" interpretation (§2):** confirmed by the operator as **no message
   bubbles in the thread UI** (text-first rendering) **and** no notification
   bubbles/chat-heads. Resolved — not open.

---

## 10. Contact names — a read-only, local lookup

TxxT resolves a sender's number to the name the operator saved for them, so a
known contact reads as a name in the conversation list, the thread header, and
notifications instead of a bare number.

- **Source: the system contacts provider.** `ContactsContract` IS the dialer's
  contact list — there is no separate dialer-private store — so reading it is
  what makes TxxT and the dialer agree about who someone is.
- **Mechanism: `PhoneLookup.CONTENT_FILTER_URI`.** The provider performs the
  carrier-specific number matching itself (country codes, trunk prefixes, short
  codes). TxxT deliberately does **not** hand-roll normalisation and
  string-compare against `Phone.NUMBER`; that re-implements the platform's
  matching rules badly and fails on exactly the formatting variance real
  carriers produce.
- **Read-only.** `READ_CONTACTS` is declared; no contacts *write* permission is,
  and nothing in the app modifies the contacts database.
  `scripts/verify_privacy_claims.py` asserts both — the read is in the expected
  set, the write is in the forbidden set.
- **No enrichment.** The lookup is a local provider query against contacts the
  operator saved themselves. Nothing is fetched to decorate an unknown number,
  which is the same posture as §8.3 ("no reverse-lookup, no contact
  enrichment").
- **Declining is a supported end state, not an error.** Without the grant every
  surface falls back to the number — never a blank row, never a crash. The same
  fallback covers a number with no matching contact, a contact with no name, and
  a provider that throws (a permission revoked mid-session). This follows the
  never-silent-failure rule the `PermissionGate` seams already carry.
- **Cached, not re-queried.** `contacts/ContactNameResolver` holds a bounded LRU
  keyed on the digit-normalised address so a list scroll never touches the
  provider. Answers are cached; failures to *ask* are not, so granting the
  permission later takes effect immediately rather than being masked by a cache
  full of numbers.
