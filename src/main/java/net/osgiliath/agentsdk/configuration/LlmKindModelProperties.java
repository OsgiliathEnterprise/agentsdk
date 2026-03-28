package net.osgiliath.agentsdk.configuration;

import net.osgiliath.agentsdk.llm.LLMS_KIND;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public class LlmKindModelProperties {

    private final Map<LLMS_KIND, ModelDefinition> kinds = new EnumMap<>(LLMS_KIND.class);
    private LLMS_KIND primaryKind = LLMS_KIND.MINI;
    private Vendor defaultVendor = Vendor.OPENAI;
    private String defaultBaseUrl;
    private String defaultApiKey;
    private Duration defaultTimeout = Duration.ofSeconds(20);

    public LLMS_KIND getPrimaryKind() {
        return primaryKind;
    }

    public void setPrimaryKind(LLMS_KIND primaryKind) {
        this.primaryKind = primaryKind;
    }

    public Vendor getDefaultVendor() {
        return defaultVendor;
    }

    public void setDefaultVendor(Vendor defaultVendor) {
        this.defaultVendor = defaultVendor;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getDefaultApiKey() {
        return defaultApiKey;
    }

    public void setDefaultApiKey(String defaultApiKey) {
        this.defaultApiKey = defaultApiKey;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public Map<LLMS_KIND, ModelDefinition> getKinds() {
        return kinds;
    }

    public ModelDefinition resolve(LLMS_KIND kind) {
        ModelDefinition override = kinds.get(kind);
        ModelDefinition resolved = new ModelDefinition();
        resolved.setVendor(override != null && override.getVendor() != null ? override.getVendor() : defaultVendor);
        resolved.setModelName(override != null && override.getModelName() != null ? override.getModelName() : kind.getName());
        resolved.setBaseUrl(override != null && override.getBaseUrl() != null ? override.getBaseUrl() : defaultBaseUrl);
        resolved.setApiKey(override != null && override.getApiKey() != null ? override.getApiKey() : defaultApiKey);
        resolved.setTimeout(override != null && override.getTimeout() != null ? override.getTimeout() : defaultTimeout);
        return resolved;
    }

    public enum Vendor {
        ANTHROPIC,
        OPENAI,
        LLAMA,
        GEMINI,
        MISTRAL
    }

    public static class ModelDefinition {
        private Vendor vendor;
        private String modelName;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;

        public Vendor getVendor() {
            return vendor;
        }

        public void setVendor(Vendor vendor) {
            this.vendor = vendor;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}

