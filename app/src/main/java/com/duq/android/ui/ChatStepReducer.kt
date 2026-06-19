package com.duq.android.ui

import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import com.duq.android.data.model.MessageStep

/**
 * Pure reducers for binding agent tool/command steps to their assistant message.
 *
 * Steps and chat deltas arrive on the same ordered WS stream and share a `runId`,
 * so a step always belongs to the reply with the matching message id. These keep
 * the merge logic Android-free and unit-testable; [ConversationViewModel] just
 * applies them to its `_messages` flow.
 */
object ChatStepReducer {

    /**
     * Upsert one tool call into the message whose id == [runId]. If that message
     * doesn't exist yet (a step can arrive before the first chat delta created
     * the bubble), an empty streaming assistant placeholder is created for it —
     * the later chat delta finds it by id instead of inserting a duplicate.
     *
     * Matched by [callId] (the gateway `toolCallId`), so the `tool` event and its
     * paired `command`/`patch` event collapse into ONE step instead of two rows.
     * The `kind:"tool"` event is authoritative for the label; a `command`/`patch`
     * event never downgrades a label already set by its tool event.
     */
    fun upsertStep(
        messages: List<Message>,
        runId: String,
        callId: String,
        label: String,
        kind: String,
        done: Boolean
    ): List<Message> {
        val base = if (messages.any { it.id == runId }) messages
        else messages + Message(id = runId, role = MessageRole.ASSISTANT, content = "", isStreaming = true)

        return base.map { m ->
            if (m.id != runId) return@map m
            val steps = m.steps.toMutableList()
            val idx = steps.indexOfFirst { it.callId == callId }
            if (idx < 0) {
                steps.add(MessageStep(callId = callId, label = label, kind = kind, done = done))
            } else {
                val cur = steps[idx]
                // Don't let a command/patch detail overwrite the authoritative
                // tool label; just carry the done state forward.
                val keepToolLabel = cur.kind == "tool" && kind != "tool"
                steps[idx] = cur.copy(
                    label = if (keepToolLabel) cur.label else label,
                    kind = if (keepToolLabel) cur.kind else kind,
                    done = done
                )
            }
            m.copy(steps = steps)
        }
    }

    /**
     * Mark every step of [runId]'s message done — used when the reply finalizes
     * (or aborts), so a step whose `phase:"end"` frame never arrived doesn't stay
     * stuck with a spinner in the collapsed block.
     */
    fun markAllStepsDone(messages: List<Message>, runId: String): List<Message> =
        messages.map { m ->
            if (m.id == runId && m.steps.any { !it.done })
                m.copy(steps = m.steps.map { it.copy(done = true) })
            else m
        }
}
