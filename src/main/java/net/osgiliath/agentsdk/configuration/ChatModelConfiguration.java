package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration that exports the auto-configured ChatModel beans with explicit names
 * for use with @AiService EXPLICIT wiring mode.
 *
 * This allows the application to use different models in different profiles (e.g., OpenAI for production, Ollama for tests)
 * while maintaining explicit control over tool providers.
 */
@Configuration
@Profile("!github")
public class ChatModelConfiguration {

    /**
     * Exports the auto-configured ChatModel bean (from langchain4j-spring-boot-starter)
     * with an explicit name for use in @AiService annotations.
     *
     * In production: Uses OpenAI ChatModel (configured via langchain4j.open-ai.chat-model.*)
     * In tests: Uses Ollama ChatModel (configured via langchain4j.ollama.chat-model.*)
     *
     * @param chatModel the auto-configured ChatModel bean (injected by Spring Boot auto-configuration)
     * @return the ChatModel bean
     */
    @Bean("primaryChatModel")
    public ChatModel primaryChatModel(ObjectProvider<ChatModel> chatModel) {
        return chatModel.getIfAvailable();
    }

    /**
     * Exports the auto-configured StreamingChatModel bean with an explicit name
     * for use in @AiService annotations.
     *
     * @param streamingChatModel the auto-configured StreamingChatModel bean
     * @return the StreamingChatModel bean
     */
    @Bean("primaryStreamingChatModel")
    public StreamingChatModel primaryStreamingChatModel(ObjectProvider<StreamingChatModel> streamingChatModel) {
        return streamingChatModel.getIfAvailable();
    }
}


