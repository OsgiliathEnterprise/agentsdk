package net.osgiliath.agentsdk.skills.acpclient;

import com.agentclientprotocol.model.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteAgentCallerTimeoutTest {

    @Test
    void processPromptTimesOutWhenGatewayNeverCompletes() throws Exception {
        RemoteAgentCaller.RemoteClientGateway blockingGateway = new RemoteAgentCaller.RemoteClientGateway() {
            @Override
            public RemoteAgentCaller.AgentInfoBridge initializeAndGetAgentInfo() {
                return new RemoteAgentCaller.AgentInfoBridge("blocking", "1.0.0");
            }

            @Override
            public void streamPrompt(String sessionId,
                                     String cwd,
                                     Map<String, String> mcpServers,
                                     String promptText,
                                     List<ContentBlock.ResourceLink> resourceLinks,
                                     RemoteAgentCaller.TokenConsumer consumer) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void close() {
            }
        };

        RemoteAgentCaller caller = new RemoteAgentCaller(blockingGateway, 100);
        OutAcpAdapter.AcpSessionBridge session = caller.createSession("timeout-session", ".", Map.of());

        Throwable thrown = null;
        try {
            session.processPrompt("hang", List.of()).get(2, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            thrown = ex.getCause();
        } catch (TimeoutException ex) {
            thrown = ex;
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown).isInstanceOfAny(java.util.concurrent.TimeoutException.class);
    }
}

