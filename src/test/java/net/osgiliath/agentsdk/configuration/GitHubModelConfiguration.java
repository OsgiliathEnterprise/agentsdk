package net.osgiliath.agentsdk.configuration;


import com.openai.models.ChatModel;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.TOOL_PROVIDER_FULL;
import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.TOOL_PROVIDER_NONE;
import static org.mockito.Mockito.mock;

/**
 * Configuration for GitHub Models when the 'github' profile is active.
 *
 * Provides custom OpenAI Official API beans configured for GitHub Models
 * with strict tool support.
 */
@Configuration
@Profile("github")
public class GitHubModelConfiguration {

    @Bean("primaryChatModel")
    @Primary
    public OpenAiOfficialChatModel model() {
        Set<Capability> capabilities = new HashSet<>();
        capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        return OpenAiOfficialChatModel.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .modelName("openai/"+ChatModel.GPT_5_NANO)
                .isGitHubModels(true)
                .apiKey(System.getenv("MODEL_TOKEN"))
                .strictJsonSchema(true)
                .supportedCapabilities(capabilities)
        .strictTools(true)
        .build();
    }
    @Bean("primaryStreamingChatModel")
    @Primary
    public OpenAiOfficialStreamingChatModel streamingModel() {
        return OpenAiOfficialStreamingChatModel.builder()
                .baseUrl("https://models.inference.ai.azure.com")
                .modelName("openai/"+ChatModel.GPT_5_NANO)
                .isGitHubModels(true)
                .apiKey(System.getenv("MODEL_TOKEN"))
        .strictTools(true)
        .build();
    }

}
