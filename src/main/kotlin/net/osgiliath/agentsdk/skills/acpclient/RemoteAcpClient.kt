package net.osgiliath.agentsdk.skills.acpclient

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.McpServer
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin Kotlin wrapper around ACP client APIs so Java callers don't need to bridge coroutines manually.
 */
class RemoteAcpClient(
    private val command: String,
    private val args: List<String>,
    private val processCwd: String
) : AutoCloseable {

    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val initializeTimeoutMillis = 30_000L
    private val promptTimeoutMillis = 30_000L
    private val logger = LoggerFactory.getLogger(RemoteAcpClient::class.java)

    @Volatile
    private var process: Process? = null

    @Volatile
    private var protocol: Protocol? = null

    @Volatile
    private var client: Client? = null

    @Volatile
    private var initializedAgentInfo: AcpAgentSupportBridge.AgentInfoBridge? = null

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun initializeAndGetAgentInfo(): AcpAgentSupportBridge.AgentInfoBridge {
        val ensuredClient = ensureClient()
        return ensureInitialized(ensuredClient)
    }

    fun streamPrompt(
        sessionId: String,
        cwd: String,
        mcpServers: Map<String, String>,
        promptText: String,
        resourceLinks: List<ContentBlock.ResourceLink>,
        consumer: AcpAgentSupportBridge.TokenConsumer
    ) {
        try {
            runBlocking {
                val session = getOrCreateSession(sessionId, cwd, mcpServers)
                val contentBlocks = mutableListOf<ContentBlock>()
                if (promptText.isNotEmpty()) {
                    contentBlocks.add(ContentBlock.Text(promptText))
                }
                contentBlocks.addAll(resourceLinks)

                withTimeout(promptTimeoutMillis) {
                    session.prompt(contentBlocks).first { event ->
                        when (event) {
                            is Event.SessionUpdateEvent -> {
                                val update = event.update
                                if (update is SessionUpdate.AgentMessageChunk) {
                                    val content = update.content
                                    if (content is ContentBlock.Text) {
                                        consumer.onNext(content.text)
                                    }
                                }
                                false
                            }

                            is Event.PromptResponseEvent -> true
                        }
                    }
                }
            }
            consumer.onComplete()
        } catch (error: TimeoutCancellationException) {
            consumer.onError(IllegalStateException("ACP prompt timed out after ${promptTimeoutMillis}ms", error))
        } catch (error: Throwable) {
            consumer.onError(error)
        }
    }

    override fun close() {
        runCatching { protocol?.close() }
        runCatching { process?.destroyForcibly() }
        sessions.clear()
        protocol = null
        process = null
        client = null
        initializedAgentInfo = null
    }

    @Synchronized
    private fun ensureClient(): Client {
        client?.let { return it }

        val fullCommand = listOf(command) + args
        logger.info("Starting ACP client process: {} in directory: {}", fullCommand.joinToString(" "), processCwd)

        val startedProcess = ProcessBuilder(fullCommand)
            .directory(File(processCwd))
            .start()

        logger.debug("ACP process started with PID: {}", startedProcess.pid())

        // Keep stderr drained so a chatty child process cannot block ACP stdio traffic.
        startErrorDrainer(startedProcess)

        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = startedProcess.inputStream.asSource().buffered(),
            output = startedProcess.outputStream.asSink().buffered()
        )

        val startedProtocol = Protocol(scope, transport)
        startedProtocol.start()

        val createdClient = Client(startedProtocol)

        process = startedProcess
        protocol = startedProtocol
        client = createdClient

        return createdClient
    }

    private fun startErrorDrainer(startedProcess: Process) {
        Thread {
            runCatching {
                startedProcess.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        logger.warn("ACP process stderr: {}", line)
                    }
                }
            }
        }.apply {
            name = "acp-remote-stderr-drainer"
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun ensureInitialized(currentClient: Client): AcpAgentSupportBridge.AgentInfoBridge {
        initializedAgentInfo?.let { return it }

        logger.debug("Sending ACP initialize request...")

        val agentInfo = try {
            runBlocking {
                withTimeout(initializeTimeoutMillis) {
                    currentClient.initialize(
                        ClientInfo(
                            implementation = Implementation("CodePromptRemoteCaller", "1.0.0")
                        )
                    )
                }
            }
        } catch (error: TimeoutCancellationException) {
            logger.error("ACP initialize timed out after {}ms. Process alive: {}",
                initializeTimeoutMillis, process?.isAlive)
            throw IllegalStateException("ACP initialize timed out after ${initializeTimeoutMillis}ms", error)
        }

        logger.info("ACP initialize successful: {} v{}",
            agentInfo.implementation?.name, agentInfo.implementation?.version)

        val implementation = agentInfo.implementation
        val resolved = AcpAgentSupportBridge.AgentInfoBridge(
            implementation?.name ?: "RemoteACPAgent",
            implementation?.version ?: "unknown"
        )
        initializedAgentInfo = resolved
        return resolved
    }

    private suspend fun getOrCreateSession(
        sessionId: String,
        cwd: String,
        mcpServers: Map<String, String>
    ): ClientSession {
        sessions[sessionId]?.let { return it }

        val currentClient = ensureClient()
        ensureInitialized(currentClient)
        val sessionParameters = SessionCreationParameters(
            cwd = cwd,
            mcpServers = toMcpServers(mcpServers)
        )

        val created = currentClient.newSession(sessionParameters) { _, _ ->
            NoopClientSessionOperations
        }

        sessions[sessionId] = created
        return created
    }

    private fun toMcpServers(mcpServers: Map<String, String>): List<McpServer> {
        return mcpServers.entries.map { (name, commandValue) ->
            McpServer.Stdio(
                name = name,
                command = commandValue,
                args = emptyList(),
                env = emptyList()
            )
        }
    }

    private object NoopClientSessionOperations : ClientSessionOperations {
        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?
        ): RequestPermissionResponse {
            return if (permissions.isEmpty()) {
                RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
            } else {
                RequestPermissionResponse(RequestPermissionOutcome.Selected(permissions.first().optionId))
            }
        }

        override suspend fun notify(
            notification: SessionUpdate,
            _meta: JsonElement?
        ) {
            // No-op: this client only relays streamed text back to the caller.
        }
    }
}
