package net.osgiliath.agentsdk.skills.acpclient;

import com.agentclientprotocol.client.ClientSession;
import com.agentclientprotocol.common.Event;
import com.agentclientprotocol.model.ContentBlock;
import com.agentclientprotocol.model.PromptResponse;
import com.agentclientprotocol.model.SessionUpdate;
import com.agentclientprotocol.model.StopReason;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.serialization.json.JsonElement;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.RETURNS_DEFAULTS;

class RemoteAcpClientStreamPromptTest {

    private static final String SESSION_ID = "s1";

    @Test
    void streamPromptRelaysTextChunksAndCompletes() throws Exception {
        RemoteAcpClient remoteAcpClient = new RemoteAcpClient("/bin/echo", List.of("ignored"), ".");

        Event.SessionUpdateEvent textChunkEvent = new Event.SessionUpdateEvent(
            newAgentMessageChunkEvent()
        );
        Event.PromptResponseEvent doneEvent = new Event.PromptResponseEvent(
            newPromptResponse()
        );
        ClientSession session = mock(ClientSession.class, invocation -> {
            if ("prompt".equals(invocation.getMethod().getName())) {
                return FlowKt.flowOf(textChunkEvent, doneEvent);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });

        setSession(remoteAcpClient, session);

        AcpAgentSupportBridge.TokenConsumer consumer = mock(AcpAgentSupportBridge.TokenConsumer.class);

        remoteAcpClient.streamPrompt(
            "s1",
            ".",
            Map.of(),
            "hello",
            List.of(),
            consumer
        );

        verify(consumer).onNext("chunk-from-agent");
        verify(consumer).onComplete();
        verify(consumer, never()).onError(any());
    }

    private static SessionUpdate.AgentMessageChunk newAgentMessageChunkEvent() throws Exception {
        Constructor<SessionUpdate.AgentMessageChunk> constructor = SessionUpdate.AgentMessageChunk.class
            .getDeclaredConstructor(ContentBlock.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new ContentBlock.Text("chunk-from-agent", null, null), null);
    }

    private static PromptResponse newPromptResponse() throws Exception {
        Constructor<PromptResponse> constructor = PromptResponse.class
            .getDeclaredConstructor(StopReason.class, String.class, JsonElement.class);
        constructor.setAccessible(true);
        return constructor.newInstance(StopReason.END_TURN, null, null);
    }

    private static void setSession(RemoteAcpClient remoteAcpClient, ClientSession session) throws Exception {
        Field sessionsField = RemoteAcpClient.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ClientSession> sessions =
            (ConcurrentHashMap<String, ClientSession>) sessionsField.get(remoteAcpClient);

        sessions.put(SESSION_ID, session);
    }
}


