package net.osgiliath.agentsdk.configuration;

import net.osgiliath.agentsdk.llm.LLMS_KIND;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmKindModelPropertiesTest {

    private LlmKindModelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LlmKindModelProperties();
    }

    @Test
    void shouldResolveWithDefaultValuesWhenNoOverride() {
        properties.setDefaultVendor(LlmKindModelProperties.Vendor.ANTHROPIC);
        properties.setDefaultBaseUrl("http://default.url");
        properties.setDefaultApiKey("default-key");
        properties.setDefaultTimeout(Duration.ofSeconds(10));

        LlmKindModelProperties.ModelDefinition resolved = properties.resolve(LLMS_KIND.MINI);

        assertEquals(LlmKindModelProperties.Vendor.ANTHROPIC, resolved.getVendor());
        assertEquals("mini", resolved.getModelName());
        assertEquals("http://default.url", resolved.getBaseUrl());
        assertEquals("default-key", resolved.getApiKey());
        assertEquals(Duration.ofSeconds(10), resolved.getTimeout());
    }

    @Test
    void shouldResolveWithOverrideValues() {
        properties.setDefaultVendor(LlmKindModelProperties.Vendor.OPENAI);
        properties.setDefaultBaseUrl("http://default.url");
        properties.setDefaultApiKey("default-key");
        properties.setDefaultTimeout(Duration.ofSeconds(20));

        LlmKindModelProperties.ModelDefinition override = new LlmKindModelProperties.ModelDefinition();
        override.setVendor(LlmKindModelProperties.Vendor.GEMINI);
        override.setModelName("gemini-pro");
        override.setBaseUrl("http://gemini.url");
        override.setApiKey("gemini-key");
        override.setTimeout(Duration.ofSeconds(30));

        properties.getKinds().put(LLMS_KIND.BIG, override);

        LlmKindModelProperties.ModelDefinition resolved = properties.resolve(LLMS_KIND.BIG);

        assertEquals(LlmKindModelProperties.Vendor.GEMINI, resolved.getVendor());
        assertEquals("gemini-pro", resolved.getModelName());
        assertEquals("http://gemini.url", resolved.getBaseUrl());
        assertEquals("gemini-key", resolved.getApiKey());
        assertEquals(Duration.ofSeconds(30), resolved.getTimeout());
    }

    @Test
    void shouldResolveWithPartialOverridesAndFallbacks() {
        properties.setDefaultVendor(LlmKindModelProperties.Vendor.OPENAI);
        properties.setDefaultBaseUrl("http://default.url");
        properties.setDefaultApiKey("default-key");
        properties.setDefaultTimeout(Duration.ofSeconds(20));

        LlmKindModelProperties.ModelDefinition override = new LlmKindModelProperties.ModelDefinition();
        override.setVendor(LlmKindModelProperties.Vendor.MISTRAL);
        // modelName, baseUrl, apiKey, timeout are left null

        properties.getKinds().put(LLMS_KIND.MEDIUM, override);

        LlmKindModelProperties.ModelDefinition resolved = properties.resolve(LLMS_KIND.MEDIUM);

        assertEquals(LlmKindModelProperties.Vendor.MISTRAL, resolved.getVendor());
        assertEquals("medium", resolved.getModelName()); // defaults to kind.getName()
        assertEquals("http://default.url", resolved.getBaseUrl()); // fallback to default
        assertEquals("default-key", resolved.getApiKey()); // fallback to default
        assertEquals(Duration.ofSeconds(20), resolved.getTimeout()); // fallback to default
    }

    @Test
    void shouldCorrectlyDefaultModelNameForDifferentKinds() {
        LlmKindModelProperties.ModelDefinition nano = properties.resolve(LLMS_KIND.NANO);
        assertEquals("nano", nano.getModelName());

        LlmKindModelProperties.ModelDefinition superKind = properties.resolve(LLMS_KIND.SUPER);
        assertEquals("super", superKind.getModelName());
    }
}
