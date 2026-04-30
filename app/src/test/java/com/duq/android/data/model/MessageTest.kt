package com.duq.android.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for Message and MessageRole
 */
class MessageTest {

    @Test
    fun `MessageRole USER toApiString returns user`() {
        assertEquals("user", MessageRole.USER.toApiString())
    }

    @Test
    fun `MessageRole ASSISTANT toApiString returns assistant`() {
        assertEquals("assistant", MessageRole.ASSISTANT.toApiString())
    }

    @Test
    fun `MessageRole fromApiString parses user`() {
        assertEquals(MessageRole.USER, MessageRole.fromApiString("user"))
    }

    @Test
    fun `MessageRole fromApiString parses assistant`() {
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromApiString("assistant"))
    }

    @Test
    fun `MessageRole fromApiString is case insensitive`() {
        assertEquals(MessageRole.USER, MessageRole.fromApiString("USER"))
        assertEquals(MessageRole.USER, MessageRole.fromApiString("User"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromApiString("ASSISTANT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MessageRole fromApiString throws on unknown role`() {
        MessageRole.fromApiString("unknown")
    }

    @Test
    fun `Message data class holds values correctly`() {
        val now = Instant.now()
        val message = Message(
            id = "msg-1",
            conversationId = "conv-123",
            role = MessageRole.USER,
            content = "Hello, Duq!",
            hasAudio = false,
            audioDurationMs = null,
            waveform = null,
            createdAt = now
        )

        assertEquals("msg-1", message.id)
        assertEquals("conv-123", message.conversationId)
        assertEquals(MessageRole.USER, message.role)
        assertEquals("Hello, Duq!", message.content)
        assertFalse(message.hasAudio)
        assertNull(message.audioDurationMs)
        assertNull(message.waveform)
        assertEquals(now, message.createdAt)
    }

    @Test
    fun `Message with audio metadata`() {
        val message = Message(
            id = "msg-2",
            conversationId = "conv-123",
            role = MessageRole.ASSISTANT,
            content = "Hello! How can I help?",
            hasAudio = true,
            audioDurationMs = 5000,
            waveform = listOf(0.1f, 0.5f, 0.8f, 0.3f),
            createdAt = Instant.now()
        )

        assertTrue(message.hasAudio)
        assertEquals(5000, message.audioDurationMs)
        assertEquals(4, message.waveform?.size)
    }

    @Test
    fun `Message copy preserves values`() {
        val original = Message(
            id = "msg-1",
            conversationId = "conv-123",
            role = MessageRole.USER,
            content = "Original",
            createdAt = Instant.now()
        )

        val copied = original.copy(content = "Modified")

        assertEquals(original.id, copied.id)
        assertEquals(original.conversationId, copied.conversationId)
        assertEquals(original.role, copied.role)
        assertEquals("Modified", copied.content)
        assertEquals(original.createdAt, copied.createdAt)
    }

    @Test
    fun `Message equals works correctly`() {
        val now = Instant.now()
        val msg1 = Message("msg-1", "conv", MessageRole.USER, "Hi", createdAt = now)
        val msg2 = Message("msg-1", "conv", MessageRole.USER, "Hi", createdAt = now)

        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())
    }
}
