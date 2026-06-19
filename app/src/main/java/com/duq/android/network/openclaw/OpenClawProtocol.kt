package com.duq.android.network.openclaw

data class OcChatEvent(
    val runId: String,
    val sessionKey: String,
    val seq: Int,
    val state: String,
    val deltaText: String? = null,
    /**
     * Authoritative cumulative assistant text from payload.message.content. The
     * gateway includes the full message-so-far on every chat frame, so the client
     * never has to re-accumulate deltas (which is fragile under any reordering).
     */
    val fullText: String? = null,
    val errorMessage: String? = null,
    val stopReason: String? = null
)

/**
 * A single step the agent performs mid-run (tool call / shell command), surfaced
 * so the user can see what it's doing during multi-step work (checking email,
 * running a command, taking a photo) instead of staring at a silent spinner.
 * Emitted from the gateway's `event:"agent" stream:"item"` frames.
 */
data class OcAgentStep(
    val runId: String,
    val itemId: String,
    val kind: String,    // "tool" | "command"
    val title: String,
    val status: String,  // "running" | "completed" | "failed"
    val phase: String    // "update" | "end"
)

/**
 * One past message from the server-side transcript (`chat.history`). The gateway
 * returns history display-normalized (tool-call XML / directive tags / NO_REPLY
 * rows already stripped), so this is render-ready.
 */
data class OcHistoryMsg(
    val role: String,  // "user" | "assistant" (gateway api strings)
    val text: String
)

enum class GatewayConnectionState { DISCONNECTED, CONNECTING, CONNECTED, PAIRING, ERROR }
