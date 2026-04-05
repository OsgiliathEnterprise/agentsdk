package net.osgiliath.agentscommon.memory;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides LangChain4j {@link dev.langchain4j.memory.ChatMemory} infrastructure for session-scoped
 * conversational context that must survive across multiple LangGraph graph invocations.
 *
 * <p>Each ACP session gets its own {@link MessageWindowChatMemory} instance backed by the shared
 * {@link InMemoryChatMemoryStore}. Nodes read/write messages through the {@link ChatMemoryProvider}
 * keyed by the session-id, so previous-turn AI messages (e.g. "Would you like to update?") are
 * visible when the next user turn arrives.</p>
 */
@Configuration
public class SessionChatMemoryConfiguration {

    /**
     * Shared in-memory store; callers can call {@code store.deleteMessages(sessionId)} to evict
     * a session (e.g. on test teardown).
     */
    @Bean
    public InMemoryChatMemoryStore sessionChatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    /**
     * Factory that returns (or reconstructs) a {@link MessageWindowChatMemory} for any session-id.
     * A window of 20 messages is more than enough to track the update-confirmation exchange.
     */
    @Bean
    public ChatMemoryProvider sessionChatMemoryProvider(InMemoryChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(store)
                .build();
    }
}

