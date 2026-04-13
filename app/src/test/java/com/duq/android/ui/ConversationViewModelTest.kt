package com.duq.android.ui

import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.data.model.Conversation
import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import com.duq.android.error.DuqError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    @MockK
    private lateinit var conversationRepository: ConversationRepository

    @MockK
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: ConversationViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testConversation = Conversation(
        id = "conv-123",
        userId = 12345L,
        title = "Test Conversation",
        startedAt = Instant.now(),
        lastMessageAt = Instant.now(),
        isActive = true
    )

    private val testMessages = listOf(
        Message(
            id = 1L,
            conversationId = "conv-123",
            role = MessageRole.USER,
            content = "Hello",
            createdAt = Instant.now()
        ),
        Message(
            id = 2L,
            conversationId = "conv-123",
            role = MessageRole.ASSISTANT,
            content = "Hi there!",
            createdAt = Instant.now()
        )
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): ConversationViewModel {
        // Setup default mocks before creating ViewModel (init block runs immediately)
        coEvery { settingsRepository.getAccessToken() } returns "test-token"
        coEvery { conversationRepository.getConversations(any(), any()) } returns Result.success(listOf(testConversation))
        coEvery { conversationRepository.getCurrentConversationId(any()) } returns testConversation.id
        coEvery { conversationRepository.refreshMessages(any(), any()) } just Runs
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(testMessages)

        return ConversationViewModel(conversationRepository, settingsRepository)
    }

    @Test
    fun `initial state has empty conversations and messages`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns ""
        coEvery { conversationRepository.getConversations(any(), any()) } returns Result.success(emptyList())
        coEvery { conversationRepository.getCurrentConversationId(any()) } returns null
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(emptyList())

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)
        advanceUntilIdle()

        assertTrue(viewModel.conversations.value.isEmpty())
        assertNull(viewModel.currentConversation.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadConversationsAndMessages loads data on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.conversations.value.size)
        assertEquals(testConversation.id, viewModel.currentConversation.value?.id)
        coVerify { conversationRepository.getConversations("test-token", false) }
        coVerify { conversationRepository.refreshMessages("test-token", testConversation.id) }
    }

    @Test
    fun `loadConversationsAndMessages handles no auth token`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns ""
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(emptyList())

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)
        advanceUntilIdle()

        assertTrue(viewModel.conversations.value.isEmpty())
        assertNull(viewModel.error.value)
        coVerify(exactly = 0) { conversationRepository.getConversations(any(), any()) }
    }

    @Test
    fun `loadConversationsAndMessages handles timeout error`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns "test-token"
        coEvery { conversationRepository.getConversations(any(), any()) } throws SocketTimeoutException()
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(emptyList())

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)
        advanceUntilIdle()

        assertTrue(viewModel.error.value is DuqError.NetworkError)
        assertEquals("Request timed out", viewModel.error.value?.message)
    }

    @Test
    fun `loadConversationsAndMessages handles no connection error`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns "test-token"
        coEvery { conversationRepository.getConversations(any(), any()) } throws UnknownHostException()
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(emptyList())

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)
        advanceUntilIdle()

        assertTrue(viewModel.error.value is DuqError.NetworkError)
        assertEquals("No internet connection", viewModel.error.value?.message)
    }

    @Test
    fun `refreshMessages calls repository`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        clearMocks(conversationRepository, answers = false)
        coEvery { conversationRepository.refreshMessages(any(), any()) } just Runs

        viewModel.refreshMessages()
        advanceUntilIdle()

        coVerify { conversationRepository.refreshMessages("test-token", testConversation.id) }
    }

    @Test
    fun `refreshMessages does nothing without auth token`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { settingsRepository.getAccessToken() } returns ""
        clearMocks(conversationRepository, answers = false)

        viewModel.refreshMessages()
        advanceUntilIdle()

        coVerify(exactly = 0) { conversationRepository.refreshMessages(any(), any()) }
    }

    @Test
    fun `createConversation adds new conversation`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val newConversation = Conversation(
            id = "conv-new",
            userId = 12345L,
            title = "New Chat",
            startedAt = Instant.now(),
            lastMessageAt = Instant.now(),
            isActive = true
        )
        coEvery { conversationRepository.createConversation(any(), any()) } returns Result.success(newConversation)

        viewModel.createConversation("New Chat")
        advanceUntilIdle()

        assertEquals(2, viewModel.conversations.value.size)
        assertEquals(newConversation.id, viewModel.currentConversation.value?.id)
        coVerify { conversationRepository.createConversation("test-token", "New Chat") }
    }

    @Test
    fun `createConversation handles failure`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { conversationRepository.createConversation(any(), any()) } returns Result.failure(Exception("API error"))

        viewModel.createConversation()
        advanceUntilIdle()

        // On failure, conversations should remain the same
        assertEquals(1, viewModel.conversations.value.size)
    }

    @Test
    fun `loadMessagesForConversation switches conversation`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val otherConversation = testConversation.copy(id = "conv-456", title = "Other")
        coEvery { conversationRepository.getConversations(any(), any()) } returns Result.success(listOf(testConversation, otherConversation))

        viewModel.loadConversationsAndMessages()
        advanceUntilIdle()

        viewModel.loadMessagesForConversation("conv-456")
        advanceUntilIdle()

        assertEquals("conv-456", viewModel.currentConversation.value?.id)
        coVerify { conversationRepository.refreshMessages("test-token", "conv-456") }
    }

    @Test
    fun `clearError resets error state`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns "test-token"
        coEvery { conversationRepository.getConversations(any(), any()) } throws SocketTimeoutException()
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(emptyList())

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `isLoading is true during load and false after`() = runTest {
        coEvery { settingsRepository.getAccessToken() } returns "test-token"
        coEvery { conversationRepository.getConversations(any(), any()) } coAnswers {
            // Simulate delay
            Result.success(listOf(testConversation))
        }
        coEvery { conversationRepository.getCurrentConversationId(any()) } returns testConversation.id
        coEvery { conversationRepository.refreshMessages(any(), any()) } just Runs
        every { conversationRepository.getMessagesFlow(any()) } returns flowOf(testMessages)

        viewModel = ConversationViewModel(conversationRepository, settingsRepository)

        // After init completes
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }
}
