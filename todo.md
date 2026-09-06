# TxxT — Remaining work

**2026-09-04.** Daily driver for three weeks. Live SIM is **proven**.
Do not spend a workstream re-proving send/receive.

Package: `com.piercingxx.txxt`  
Default SMS/MMS handler. Privacy-first, AMOLED, everything leaky off by
default. No RCS. No `RECORD_AUDIO`.

```
Status: role held, photos, search, mute, theme sync, conversation export
shipped. Settings backup is settings+blocklist only. Quarantine
disposition DROPS messages. Switching the default SMS app destroys the
archive unless exported first.
```

---

## Locked now (2026-09-04)

| ID | Decision |
|---|---|
| T1 | Live SIM is done. Next is **complete the product**: export, then quarantine store. |
| T2 | Optional auto-reply stays **off** (PRIVACY.md §5). |
| T3 | Starred senders never quarantine (PRIVACY.md §6). |

---

## Workstreams

### T1 — User-visible conversation export

History lives only in `txxt.db`. Role-switch or uninstall drops it.
Settings already dump settings + blocklist into app-private storage.

- [x] SAF create-document **or** share-sheet export of conversations +
  messages (and say so in the UI).
- [x] Include MMS photo references honestly (export the files or write
  “photos not in this JSON”).
- [x] Restore through `RestoreService` **without** REPLACE-destroying live ids.
- [x] README / FEATURES stop calling settings-dump “JSON export.”
- **Accept:** export on device A, import on a fresh install, threads and
  bodies match. A second import does not duplicate every message.
  (Device confirm is T4 — not claimed here.)

### T2 — Quarantine is a store, not a drop

`InboundFilter` already returns QUARANTINE. Deliver receivers discard.
That is data loss. **Default off** until the store exists.

- [ ] Persist quarantined inbound SMS/MMS (unread, flagged).
- [ ] Hide them from the main list (same idea as archive).
- [ ] Review surface: deliver / block / delete per sender.
- [ ] Enabling the toggle with no store must be impossible (hide or disable).
- **Accept:** unknown sender + quarantine on → message is in the review
  list, not gone. Starred sender still lands in the inbox.

### T3 — Role-switch warning

- [ ] Before the user leaves default-SMS, warn that the local archive
  dies unless they exported.
- **Accept:** the system role UI is out of our hands; the in-app “unset”
  / first-run copy is honest.

### T5 — Pin and mute-until

Daily-driver quality of life. Not a new privacy surface.

- [ ] **Pin** a thread to the top of the conversation list. Cap it (e.g.
  5). Pinned stay above the rest; unpin is one tap.
- [ ] **Mute until** a wall time or duration (1h / 8h / tonight / Monday).
  Notifications for that thread stay off until then; the thread still
  receives. Existing mute (forever) stays.
- **Accept:** pin two threads, kill the app, they are still on top.
  Mute-until 1h: no notification; after the hour, the next SMS notifies.

### T4 — Device confirmation (not a SIM re-prove)

Already daily. Spot-check after T1/T2:

- [ ] Export + restore on this phone.
- [ ] Quarantine on → unknown SMS held; toggle off → normal deliver.
- [ ] Verizon MMS photo still sends (existing path).
- [ ] Pin + mute-until survive process death (T5).
- **Accept:** dated note in this file.

---

## Deferred (do not start)

Burn-after-read, biometric lock, auto-reply SMS, scheduled send, RCS,
link previews, bubbles.

---

## Stop conditions

- `RECORD_AUDIO` / RCS / analytics → reject.
- Shipping quarantine while receivers still drop → reject.
- Calling settings-only dump “message export” → reject.

---

## Suggested order

1. T1 (the archive can vanish today)
2. T2 (do not enable a drop-path)
3. T5 pin / mute-until
4. T3 copy
5. T4 spot-check
