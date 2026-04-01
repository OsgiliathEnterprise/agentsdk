package net.osgiliath.agentsdk.llm;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import net.osgiliath.agentsdk.configuration.LlmKindModelProperties.ModelDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.CHAT_MODEL_HTTP_CLIENT_BUILDER;
import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.STREAMING_CHAT_MODEL_HTTP_CLIENT_BUILDER;

/**
 * Factory bean responsible for creating model instances from model definitions.
 */
@Component
@Profile("!github")
public class ChatModelFactory {

    private final HttpClientBuilder streamingChatModelHttpClient;
    private final HttpClientBuilder chatModelHttpClientBuilder;

    public ChatModelFactory(@Qualifier(STREAMING_CHAT_MODEL_HTTP_CLIENT_BUILDER) HttpClientBuilder streamingChatModelHttpClient, @Qualifier(CHAT_MODEL_HTTP_CLIENT_BUILDER) HttpClientBuilder chatModelHttpClientBuilder) {
        this.streamingChatModelHttpClient = streamingChatModelHttpClient;
        this.chatModelHttpClientBuilder = chatModelHttpClientBuilder;
    }

    public ChatModel createChatModel(ModelDefinition definition) {
        return switch (definition.getVendor()) {
            case ANTHROPIC -> anthropicChatModel(definition);
            case OPENAI -> openAiChatModel(definition);
            case LLAMA -> ollamaChatModel(definition);
            case GEMINI -> geminiChatModel(definition);
            case MISTRAL -> mistralChatModel(definition);
        };
    }

    public StreamingChatModel createStreamingChatModel(ModelDefinition definition) {
        return switch (definition.getVendor()) {
            case ANTHROPIC -> anthropicStreamingChatModel(definition);
            case OPENAI -> openAiStreamingChatModel(definition);
            case LLAMA -> ollamaStreamingChatModel(definition);
            case GEMINI -> geminiStreamingChatModel(definition);
            case MISTRAL -> mistralStreamingChatModel(definition);
        };
    }

    private ChatModel anthropicChatModel(ModelDefinition definition) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private StreamingChatModel anthropicStreamingChatModel(ModelDefinition definition) {
        AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder = AnthropicStreamingChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private ChatModel openAiChatModel(ModelDefinition definition) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private StreamingChatModel openAiStreamingChatModel(ModelDefinition definition) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(streamingChatModelHttpClient)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private ChatModel ollamaChatModel(ModelDefinition definition) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        return builder.build();
    }

    private StreamingChatModel ollamaStreamingChatModel(ModelDefinition definition) {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(streamingChatModelHttpClient)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        return builder.build();
    }

    private ChatModel geminiChatModel(ModelDefinition definition) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private StreamingChatModel geminiStreamingChatModel(ModelDefinition definition) {
        GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder = GoogleAiGeminiStreamingChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(streamingChatModelHttpClient)
                .timeout(definition.getTimeout());
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private ChatModel mistralChatModel(ModelDefinition definition) {
        MistralAiChatModel.MistralAiChatModelBuilder builder = MistralAiChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(chatModelHttpClientBuilder)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private StreamingChatModel mistralStreamingChatModel(ModelDefinition definition) {
        MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder builder = MistralAiStreamingChatModel.builder()
                .modelName(definition.getModelName())
                .httpClientBuilder(streamingChatModelHttpClient)
                .timeout(definition.getTimeout());
        if (hasText(definition.getBaseUrl())) {
            builder.baseUrl(definition.getBaseUrl());
        }
        if (hasText(definition.getApiKey())) {
            builder.apiKey(definition.getApiKey());
        }
        return builder.build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

