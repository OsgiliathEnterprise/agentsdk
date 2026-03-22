package net.osgiliath.agentsdk.skills.acpclient

import com.agentclientprotocol.common.FileSystemOperations
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolKind
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class RemoteAcpClientProtocolParityTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `createClientInfo advertises filesystem capabilities and implementation`() {
        val client = RemoteAcpClient("/bin/echo", emptyList(), ".")

        val clientInfo = client.createClientInfo()

        assertEquals(RemoteAcpClient.CLIENT_IMPLEMENTATION_NAME, clientInfo.implementation?.name)
        assertEquals(RemoteAcpClient.CLIENT_IMPLEMENTATION_VERSION, clientInfo.implementation?.version)
        assertNotNull(clientInfo.capabilities.fs)
        assertTrue(clientInfo.capabilities.fs!!.readTextFile)
        assertTrue(clientInfo.capabilities.fs!!.writeTextFile)
    }

    @Test
    fun `createSessionOperations exposes filesystem access rooted at cwd and grants first permission`() = runBlocking {
        val client = RemoteAcpClient("/bin/echo", emptyList(), ".")
        Files.writeString(tempDir.resolve("README.md"), "hello from workspace")

        val operations = client.createSessionOperations(tempDir.toString())
        val fileSystemOperations = assertInstanceOf(FileSystemOperations::class.java, operations)

        val readResponse = fileSystemOperations.fsReadTextFile("README.md", null, null, null)
        assertEquals("hello from workspace", readResponse.content)

        fileSystemOperations.fsWriteTextFile("notes/out.txt", "written by ACP", null)
        assertEquals("written by ACP", tempDir.resolve("notes/out.txt").readText())

        val permissionResponse = operations.requestPermissions(
            toolCall = SessionUpdate.ToolCallUpdate(
                toolCallId = ToolCallId("tool-1"),
                title = "Read README",
                kind = ToolKind.OTHER,
                rawInput = null
            ),
            permissions = listOf(
                PermissionOption(PermissionOptionId("allow-once"), "Allow once", PermissionOptionKind.ALLOW_ONCE),
                PermissionOption(PermissionOptionId("reject-once"), "Reject once", PermissionOptionKind.REJECT_ONCE)
            ),
            _meta = null
        )
        val selected = assertInstanceOf(RequestPermissionOutcome.Selected::class.java, permissionResponse.outcome)
        assertEquals("allow-once", selected.optionId.value)
    }

    @Test
    fun `createSessionOperations cancels permission requests when no options are available`() {
        runBlocking {
            val client = RemoteAcpClient("/bin/echo", emptyList(), ".")
            val operations = client.createSessionOperations(tempDir.toString())

            val response = operations.requestPermissions(
                toolCall = SessionUpdate.ToolCallUpdate(
                    toolCallId = ToolCallId("tool-2"),
                    title = "No options",
                    kind = ToolKind.OTHER,
                    rawInput = null
                ),
                permissions = emptyList(),
                _meta = null
            )

            assertInstanceOf(RequestPermissionOutcome.Cancelled::class.java, response.outcome)
        }
    }

    @Test
    fun `toMcpServers maps aliases to standard stdio MCP declarations`() {
        val client = RemoteAcpClient("/bin/echo", emptyList(), ".")

        val mcpServers = client.toMcpServers(
            mapOf(
                "ExternalAssistant" to "cagent",
                "repo-tools" to "repo-tools"
            )
        )

        assertEquals(2, mcpServers.size)
        val stdioServers = mcpServers.map { assertInstanceOf(com.agentclientprotocol.model.McpServer.Stdio::class.java, it) }
        assertEquals(listOf("ExternalAssistant", "repo-tools"), stdioServers.map { it.name })
        assertEquals(listOf("cagent", "repo-tools"), stdioServers.map { it.command })
        assertTrue(stdioServers.all { it.args.isEmpty() })
        assertTrue(stdioServers.all { it.env.isEmpty() })
    }

    @Test
    fun `resolveProjectDir normalizes blank cwd to the current working directory`() {
        val client = RemoteAcpClient("/bin/echo", emptyList(), ".")

        val resolved = client.resolveProjectDir(" ")

        assertEquals(Path.of(".").toAbsolutePath().normalize(), resolved)
    }
}



