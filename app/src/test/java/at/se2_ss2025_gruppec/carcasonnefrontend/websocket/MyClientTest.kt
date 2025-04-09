package at.se2_ss2025_gruppec.carcasonnefrontend.websocket

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import org.hildan.krossbow.stomp.subscribeText
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompClientTest {

    private lateinit var stompClient: MyClient
    private lateinit var session: StompSession
    private lateinit var testCallbacks: TestCallbacks

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        session = mockk(relaxed = true)
        testCallbacks = TestCallbacks()
        stompClient = spyk(MyClient(testCallbacks), recordPrivateCalls = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }


//    @Test
//    fun `connect should establish session successfully`() = runTest {
//        // Arrange
//        whenever(mockKrossbowClient.connect(anyString())).thenReturn(mockSession)
//
//        // Act
//        stompClient.connect()
//
//        // Give time for the coroutine to complete
//        testDispatcher.scheduler.advanceUntilIdle()
//
//        // Assert
//        verify(mockKrossbowClient).connect(eq("ws://10.0.2.2:8080/ws/game/websocket"))
//        verify(callbacks).onResponse(eq("WebSocket connected"))
//        assertTrue(stompClient.isConnected())
//    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `connect should call callback on success`() = testScope.runTest {
        val mockClient = mockk<StompClient>()
        coEvery { mockClient.connect(any()) } returns session
        stompClient.apply {
            this["client"] = mockClient
        }

        stompClient.connect()
        advanceUntilIdle()

        assertTrue(testCallbacks.messages.any { it.contains("connected", ignoreCase = true) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `connect should call callback on failure`() = testScope.runTest {
        val mockClient = mockk<StompClient>()
        coEvery { mockClient.connect(any()) } throws RuntimeException("Connection error")
        stompClient.apply {
            this["client"] = mockClient
        }

        stompClient.connect()
        advanceUntilIdle()

        assertTrue(testCallbacks.messages.any { it.contains("Connection failed", ignoreCase = true) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sendHello logs error when session is null`() = testScope.runTest {
        stompClient.sendHello()
        advanceUntilIdle()
        assertFalse(stompClient.isConnected())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sendJson logs error when session is null`() = testScope.runTest {
        stompClient.sendJson()
        advanceUntilIdle()
        assertFalse(stompClient.isConnected())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sendStartGame logs error when session is null`() = testScope.runTest {
        stompClient.sendStartGame("game-42")
        advanceUntilIdle()
        assertFalse(stompClient.isConnected())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `listenOn should collect flow from session`() = testScope.runTest {
        val fakeSession = mockk<StompSession>()
        val msg = "test-message"
        coEvery { fakeSession.subscribeText("/topic/test") } returns flowOf(msg)

        // Inject mock session
        stompClient.apply {
            this["session"] = fakeSession
        }

        var result: String? = null

        stompClient.listenOn("/topic/test") {
            result = it
        }

        advanceUntilIdle()

        assertEquals(msg, result)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `listenOn should do nothing if session is null`() = testScope.runTest {
        stompClient.listenOn("/topic/empty") { _ -> fail("Should not be called") }
        advanceUntilIdle()
    }

    @Test
    fun `isConnected returns false when session is null`() {
        assertFalse(stompClient.isConnected())
    }

    @Test
    fun `isConnected returns true when session is set`() {
        stompClient.apply {
            this["session"] = session
        }
        assertTrue(stompClient.isConnected())
    }

    // Helper callback to capture messages
    class TestCallbacks : Callbacks {
        val messages = mutableListOf<String>()
        override fun onResponse(res: String) {
            messages.add(res)
        }
    }

    // Helper to set private/protected field by name
    private operator fun Any.set(fieldName: String, value: Any?) {
        val field = this::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(this, value)
    }
}
