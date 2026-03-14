package net.osgiliath.agentsdk.skills.acpclient;

import com.agentclientprotocol.model.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteAgentCallerSessionPropagationTest {

    @Test
    void forwardsSessionContextToRemoteGateway() {
        CapturingGateway gateway = new CapturingGateway();
        RemoteAgentCaller caller = new RemoteAgentCaller(gateway);

        OutAcpAdapter.AcpSessionBridge session = caller.createSession(
            "remote-session",
            "/workspace/app",
            Map.of("mcp-a", "http://localhost:8080")
        );

        String response = session.processPrompt("inspect", List.of()).join();

        assertThat(session.getSessionId()).isEqualTo("remote-session");
        assertThat(response).isEqualTo("remote:inspect");
        assertThat(gateway.sessionId.get()).isEqualTo("remote-session");
        assertThat(gateway.cwd.get()).isEqualTo("/workspace/app");
        assertThat(gateway.mcpServers.get()).containsExactlyEntriesOf(Map.of("mcp-a", "http://localhost:8080"));
        assertThat(gateway.promptText.get()).isEqualTo("inspect");
        assertThat(gateway.resourceLinks).isEmpty();
    }

    private static final class CapturingGateway implements RemoteAgentCaller.RemoteClientGateway {
        private final AtomicReference<String> sessionId = new AtomicReference<>();
        private final AtomicReference<String> cwd = new AtomicReference<>();
        private final AtomicReference<Map<String, String>> mcpServers = new AtomicReference<>(Map.of());
        private final AtomicReference<String> promptText = new AtomicReference<>();
        private final List<ContentBlock.ResourceLink> resourceLinks = new CopyOnWriteArrayList<>();

        @Override
        public RemoteAgentCaller.AgentInfoBridge initializeAndGetAgentInfo() {
            return new RemoteAgentCaller.AgentInfoBridge("remote", "1.0.0");
        }

        @Override
        public void streamPrompt(String sessionId,
                                 String cwd,
                                 Map<String, String> mcpServers,
                                 String promptText,
                                 List<ContentBlock.ResourceLink> resourceLinks,
                                 RemoteAgentCaller.TokenConsumer consumer) {
            this.sessionId.set(sessionId);
            this.cwd.set(cwd);
            this.mcpServers.set(mcpServers);
            this.promptText.set(promptText);
            this.resourceLinks.addAll(resourceLinks);
            consumer.onNext("remote:" + promptText);
            consumer.onComplete();
        }

        @Override
        public void close() {
        }
    }
}
