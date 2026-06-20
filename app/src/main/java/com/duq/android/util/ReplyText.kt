package com.duq.android.util

/**
 * Normalizes assistant reply text before it is shown to the user.
 *
 * The app subscribes to the RAW session feed (`sessions.messages.subscribe`),
 * which broadcasts every frame of the `main` session — including heartbeat
 * self-polls and engine tool-failure surfaces. The gateway strips these from
 * `chat.history` and from channel delivery, but the live WS stream carries them
 * verbatim, so the client must normalize anything it renders to match.
 *
 * Stripped here:
 *  - `NO_REPLY` — "don't surface anything", alone or appended to real text
 *    ("Астана, без изменений.\n\nNO_REPLY").
 *  - `HEARTBEAT_OK` — heartbeat ack sentinel. Heartbeat runs in the main session
 *    (full context, by design), so its acks leak into the feed. Never for the user.
 *  - `SESSION_STATUS` — display marker of the built-in `session_status` tool;
 *    it leaks as a prefix glued to the reply ("SESSION_STATUSПроблема — …").
 *  - Engine tool-failure notices — lines the engine emits when an op fails, e.g.
 *    "⚠️ 📝 Edit: `…/USER.md` failed", "⚠️ ✉️ Message: `120` failed",
 *    "⚠️ 🛠️ `gog gmail …` failed", "⚠️ ⏰ Cron failed". Raw tech noise; the real
 *    failure is still visible in the collapsed tool-steps block (status=failed)
 *    and in server logs.
 */
object ReplyText {

    // Control/tool sentinels that must never be shown to the user. Matched
    // case-insensitively; SESSION_STATUS can be glued directly to following text
    // (incl. Cyrillic), so it is NOT word-boundary anchored.
    private val SENTINELS = listOf(
        Regex("(?i)\\bno_reply\\b"),
        Regex("(?i)\\bheartbeat_ok\\b"),
        Regex("(?i)SESSION_STATUS"),
    )

    // Engine tool-failure surface: a whole line starting with the ⚠️ warning sign
    // (U+26A0, optional variation selector U+FE0F) and ending in "failed". DUQ's
    // own ⚠️ usage (strain cards "⚠️: сухость рта") doesn't end in "failed", so
    // this won't clip legitimate content.
    private val TOOL_FAILURE_NOTICE =
        Regex("(?im)^[ \\t]*\\u26A0\\uFE0F?[^\\n]*\\bfailed[ \\t]*$")

    // Collapse blank-line runs left behind after stripping mid-text lines.
    private val BLANK_RUNS = Regex("\\n{3,}")

    /** Strips control/tool sentinels + engine failure notices and tidies whitespace. */
    fun clean(text: String): String {
        var out = TOOL_FAILURE_NOTICE.replace(text, "")
        out = SENTINELS.fold(out) { acc, re -> re.replace(acc, "") }
        out = BLANK_RUNS.replace(out, "\n\n")
        return out.trim()
    }

    /** True when the message is only sentinels/notices and should be suppressed. */
    fun isSuppressed(text: String): Boolean = clean(text).isEmpty()
}
