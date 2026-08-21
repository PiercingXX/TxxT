package com.piercingxx.txxt.block

/**
 * The outcome of filtering an inbound message.
 *
 * [DELIVER] — message passes through to the normal conversation thread.
 * [QUARANTINE] — message is held aside; the user can review and release it later.
 * [BLOCK] — message is rejected outright; no notification is shown.
 */
enum class MessageDisposition {
    DELIVER,
    QUARANTINE,
    BLOCK,
}
