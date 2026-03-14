package net.osgiliath.agentsdk.skills.acpclient;

import com.agentclientprotocol.model.ContentBlock;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RemoteAgentCaller implements OutAcpAdapter {

    private static final long DEFAULT_PROCESS_PROMPT_TIMEOUT_MILLIS = 30_000L;
    private static final Logger log = LoggerFactory.getLogger(RemoteAgentCaller.class);
    private final RemoteClientGateway remoteClient;
    private final AtomicReference<AgentInfoBridge> agentInfoRef = new AtomicReference<>();
    private final long processPromptTimeoutMillis;

    @org.springframework.beans.factory.annotation.Autowired
    public RemoteAgentCaller(
        @Value("${codeprompt.acp.remote.command:java}") String command,
        @Value("${codeprompt.acp.remote.args:}") String args,
        @Value("${codeprompt.acp.remote.cwd:.}") String processCwd
    ) {
        this(new RemoteAcpClientGateway(new RemoteAcpClient(command, parseArgs(args), processCwd)), DEFAULT_PROCESS_PROMPT_TIMEOUT_MILLIS);
    }

    public RemoteAgentCaller(RemoteClientGateway remoteClient) {
        this(remoteClient, DEFAULT_PROCESS_PROMPT_TIMEOUT_MILLIS);
    }

    public RemoteAgentCaller(RemoteClientGateway remoteClient, long processPromptTimeoutMillis) {
        this.remoteClient = remoteClient;
        this.processPromptTimeoutMillis = processPromptTimeoutMillis;
    }

    @Override
    public AgentInfoBridge getAgentInfo() {
        AgentInfoBridge cached = agentInfoRef.get();
        if (cached != null) {
            return cached;
        }
        AgentInfoBridge initialized = remoteClient.initializeAndGetAgentInfo();
        agentInfoRef.compareAndSet(null, initialized);
        return agentInfoRef.get();
    }

    @Override
    public AcpSessionBridge createSession(String sessionId, String cwd, Map<String, String> mcpServers) {
        String effectiveCwd = cwd == null || cwd.isBlank() ? "." : cwd;
        Map<String, String> effectiveMcpServers = mcpServers == null ? Collections.emptyMap() : mcpServers;
        log.info("Creating remote ACP session {} in {} with {} MCP server(s)", sessionId, effectiveCwd, effectiveMcpServers.size());
        return new RemoteSession(sessionId, effectiveCwd, effectiveMcpServers);
    }

    @PreDestroy
    public void shutdown() {
        remoteClient.close();
    }

    private static List<String> parseArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawArgs.split("\\s+"))
            .filter(part -> !part.isBlank())
            .toList();
    }

    private final class RemoteSession implements AcpSessionBridge {
        private final String sessionId;
        private final String cwd;
        private final Map<String, String> mcpServers;

        private RemoteSession(String sessionId, String cwd, Map<String, String> mcpServers) {
            this.sessionId = sessionId;
            this.cwd = cwd;
            this.mcpServers = mcpServers;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public CompletableFuture<String> processPrompt(String promptText, List<ContentBlock.ResourceLink> resourceLinks) {
            CompletableFuture<String> response = new CompletableFuture<>();
            StringBuilder fullResponse = new StringBuilder();

            // Stream in a separate task so a blocked stream call cannot block processPrompt() itself.
            streamPrompt(promptText, resourceLinks, new TokenConsumer() {
                @Override
                public void onNext(String token) {
                    fullResponse.append(token);
                }

                @Override
                public void onComplete() {
                    response.complete(fullResponse.toString());
                }

                @Override
                public void onError(Throwable error) {
                    response.completeExceptionally(error);
                }
            });

            return response.orTimeout(processPromptTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public void streamPrompt(String promptText, List<ContentBlock.ResourceLink> resourceLinks, TokenConsumer consumer) {
            List<ContentBlock.ResourceLink> safeLinks = resourceLinks == null ? List.of() : resourceLinks;
            log.debug("Streaming remote prompt for session {} from cwd {} with {} resource link(s)", sessionId, cwd, safeLinks.size());
            try {
                remoteClient.streamPrompt(sessionId, cwd, mcpServers, promptText == null ? "" : promptText, safeLinks, consumer);
            } catch (Throwable error) {
                consumer.onError(error);
            }
        }
    }

    public interface RemoteClientGateway {
        AgentInfoBridge initializeAndGetAgentInfo();

        /**
         * Streams the prompt response from the remote agent, invoking the provided consumer's callbacks as tokens are received,
         * the stream completes, or an error occurs.
         *
         * @param sessionId    The unique identifier for the session.
         * @param cwd          The current working directory context for the agent.
         * @param mcpServers   A map of MCP server identifiers to their connection details.
         * @param promptText   The text of the prompt to send to the agent.
         * @param resourceLinks A list of resource links to provide context for the prompt.
         * @param consumer      The consumer whose callbacks will be invoked with streaming tokens, completion, or errors.
         */
        void streamPrompt(
            String sessionId,
            String cwd,
            Map<String, String> mcpServers,
            String promptText,
            List<ContentBlock.ResourceLink> resourceLinks,
            TokenConsumer consumer
        );

        void close();
    }

    private static final class RemoteAcpClientGateway implements RemoteClientGateway {
        private final RemoteAcpClient delegate;

        private RemoteAcpClientGateway(RemoteAcpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentInfoBridge initializeAndGetAgentInfo() {
            return delegate.initializeAndGetAgentInfo();
        }

        @Override
        public void streamPrompt(
            String sessionId,
            String cwd,
            Map<String, String> mcpServers,
            String promptText,
            List<ContentBlock.ResourceLink> resourceLinks,
            TokenConsumer consumer
        ) {
            delegate.streamPrompt(sessionId, cwd, mcpServers, promptText, resourceLinks, consumer);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
