package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.AppSettings
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.data.SandboxController
import com.inspiredandroid.kai.data.createLocalInferenceEngine
import com.inspiredandroid.kai.testutil.FakeDataRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteDataRepositoryFallbackIT {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `on-device provider stays after remote provider in full repository ordering`() = testScope.runTest {
        // Arrange: configure AppSettings with remote and LiteRT services
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        // Create sandbox controller (required for RemoteDataRepository)
        val sandboxController = FakeSandboxController()
        appSettings.setSandboxController(sandboxController)

        // Configure services: remote (OpenAI-Compatible) and on-device LiteRT
        val remoteInstanceId = appSettings.generateInstanceId(Service.OpenAICompatible.id)
        appSettings.setConfiguredServiceInstances(
            listOf(
                ServiceInstance(
                    instanceId = remoteInstanceId,
                    serviceId = Service.OpenAICompatible.id,
                ),
                ServiceInstance(
                    instanceId = "litert-instance",
                    serviceId = Service.LiteRT.id,
                ),
            )
        )

        // Create RemoteDataRepository with real LocalInferenceEngine
        val remoteDataRepository = RemoteDataRepository(
            requests = appSettings.requests,
            appSettings = appSettings,
            conversationStorage = appSettings.conversationStorage,
            toolExecutor = appSettings.toolExecutor,
            memoryStore = appSettings.memoryStore,
            taskStore = appSettings.taskStore,
            heartbeatManager = appSettings.heartbeatManager,
            emailStore = appSettings.emailStore,
            emailPoller = appSettings.emailPoller,
            smsStore = appSettings.smsStore,
            smsPoller = appSettings.smsPoller,
            smsReader = appSettings.smsReader,
            smsPermissionController = appSettings.smsPermissionController,
            smsSendPermissionController = appSettings.smsSendPermissionController,
            smsSender = appSettings.smsSender,
            smsDraftStore = appSettings.smsDraftStore,
            notificationStore = appSettings.notificationStore,
            notificationListenerController = appSettings.notificationListenerController,
            mcpServerManager = appSettings.mcpServerManager,
            skillManager = appSettings.skillManager,
            sandboxController = sandboxController,
            localInferenceEngine = createLocalInferenceEngine(),
        )

        // Act: get the ordered fallback entries
        val fallbackEntries = remoteDataRepository.getOrderedFallbackEntries()

        // Assert: LiteRT should appear after remote provider
        assertEquals(2, fallbackEntries.size)
        assertEquals(Service.OpenAICompatible.id, fallbackEntries[0].service.id)
        assertEquals(Service.LiteRT.id, fallbackEntries[1].service.id)
    }

    // Helper class to satisfy RemoteDataRepository constructor requirements
    private class FakeSandboxController : com.inspiredandroid.kai.SandboxController {
        override val status: kotlinx.coroutines.flow.StateFlow<com.inspiredandroid.kai.SandboxStatus> = 
            kotlinx.coroutines.flow.MutableStateFlow(com.inspiredandroid.kai.SandboxStatus())
        override val sessions: kotlinx.coroutines.flow.StateFlow<List<String>> = 
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override fun setup() {}
        override fun cancel() {}
        override fun reset() {}
        override fun installPackages() {}
        override suspend fun executeCommand(command: String, sessionId: String): String = ""
        override suspend fun executeCommandStreaming(
            command: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            sessionId: String,
        ): CommandHandle = com.inspiredandroid.kai.tools.NoOpCommandHandle()
        override suspend fun listDirectory(path: String): List<com.inspiredandroid.kai.SandboxFileEntry> = emptyList()
        override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): com.inspiredandroid.kai.SandboxFileResult = 
            com.inspiredandroid.kai.SandboxFileResult.Unreadable
        override suspend fun writeTextFile(path: String, content: String): Boolean = false
        override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
        override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
        override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
        override suspend fun importFile(directoryPath: String, source: com.inspiredandroid.kai.filekit.PlatformFile): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
    }
}