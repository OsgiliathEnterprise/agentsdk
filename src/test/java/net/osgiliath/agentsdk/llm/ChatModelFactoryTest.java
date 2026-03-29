package net.osgiliath.agentsdk.llm;

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
import net.osgiliath.agentsdk.configuration.LlmKindModelProperties.Vendor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelFactoryTest {

    static {
        System.setProperty("langchain4j.http.clientBuilderFactory", "dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");
    }

    private final ChatModelFactory factory = new ChatModelFactory(null, null);

    @Test
    void testCreateAnthropicChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.ANTHROPIC);
        definition.setModelName("claude-3-opus-20240229");
        definition.setBaseUrl("https://api.anthropic.com");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(AnthropicChatModel.class);
    }

    @Test
    void testCreateAnthropicStreamingChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.ANTHROPIC);
        definition.setModelName("claude-3-opus-20240229");
        definition.setBaseUrl("https://api.anthropic.com");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        StreamingChatModel model = factory.createStreamingChatModel(definition);

        assertThat(model).isInstanceOf(AnthropicStreamingChatModel.class);
    }

    @Test
    void testCreateOpenAiChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.OPENAI);
        definition.setModelName("gpt-4");
        definition.setBaseUrl("https://api.openai.com/v1");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void testCreateOpenAiStreamingChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.OPENAI);
        definition.setModelName("gpt-4");
        definition.setBaseUrl("https://api.openai.com/v1");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        StreamingChatModel model = factory.createStreamingChatModel(definition);

        assertThat(model).isInstanceOf(OpenAiStreamingChatModel.class);
    }

    @Test
    void testCreateOllamaChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.LLAMA);
        definition.setModelName("llama3");
        definition.setBaseUrl("http://localhost:11434");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(OllamaChatModel.class);
    }

    @Test
    void testCreateOllamaStreamingChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.LLAMA);
        definition.setModelName("llama3");
        definition.setBaseUrl("http://localhost:11434");
        definition.setTimeout(Duration.ofSeconds(30));

        StreamingChatModel model = factory.createStreamingChatModel(definition);

        assertThat(model).isInstanceOf(OllamaStreamingChatModel.class);
    }

    @Test
    void testCreateGeminiChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.GEMINI);
        definition.setModelName("gemini-pro");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(GoogleAiGeminiChatModel.class);
    }

    @Test
    void testCreateGeminiStreamingChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.GEMINI);
        definition.setModelName("gemini-pro");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        StreamingChatModel model = factory.createStreamingChatModel(definition);

        assertThat(model).isInstanceOf(GoogleAiGeminiStreamingChatModel.class);
    }

    @Test
    void testCreateMistralChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.MISTRAL);
        definition.setModelName("mistral-large-latest");
        definition.setBaseUrl("https://api.mistral.ai");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(MistralAiChatModel.class);
    }

    @Test
    void testCreateMistralStreamingChatModel() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.MISTRAL);
        definition.setModelName("mistral-large-latest");
        definition.setBaseUrl("https://api.mistral.ai");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        StreamingChatModel model = factory.createStreamingChatModel(definition);

        assertThat(model).isInstanceOf(MistralAiStreamingChatModel.class);
    }

    @Test
    void testCreateAnthropicChatModelWithoutBaseUrl() {
        ModelDefinition definition = new ModelDefinition();
        definition.setVendor(Vendor.ANTHROPIC);
        definition.setModelName("claude-3-opus-20240229");
        definition.setApiKey("api-key");
        definition.setTimeout(Duration.ofSeconds(30));

        ChatModel model = factory.createChatModel(definition);

        assertThat(model).isInstanceOf(AnthropicChatModel.class);
    }
}
