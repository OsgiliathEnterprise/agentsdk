package net.osgiliath.agentsdk.langgraph.edge;

import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.ChatState;

import org.bsc.langgraph4j.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LLMToToolEdge implements EdgeAction<ChatState> {
    private static final Logger log = LoggerFactory.getLogger(LLMToToolEdge.class);
    /**
     * Conditional edge that inspects the last message after the agent node.
     * <ul>
     *   <li>If the last message is an {@link AiMessage} with tool execution
     *       requests → routes to {@code "next"} (tools node).</li>
     *   <li>Otherwise → routes to {@code "exit"} ({@code END}).</li>
     * </ul>
     */
    @Override
    public String apply(ChatState state) {
        var lastMessage = state.lastMessage()
                .orElseThrow(() -> new IllegalStateException("last message not found!"));

        log.debug("routeMessage: {}", lastMessage);

        if (lastMessage instanceof AiMessage message) {
            if (message.hasToolExecutionRequests()) {
                return "next";
            }
        }

        // No tool calls → we can finish (respond to the user)
        return "exit";
    }
}
