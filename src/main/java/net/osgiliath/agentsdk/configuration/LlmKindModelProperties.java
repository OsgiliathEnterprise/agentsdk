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
        if (override == null) {
            ModelDefinition definition = new ModelDefinition();
            definition.setVendor(defaultVendor);
            definition.setModelName(kind.getName());
            definition.setBaseUrl(defaultBaseUrl);
            definition.setApiKey(defaultApiKey);
            definition.setTimeout(defaultTimeout);
            return definition;
        } else if (!override.isUseDefaultIfNoOverride()) {
            return override;
        } else {
            ModelDefinition definition = new ModelDefinition();
            definition.setVendor(override.getVendor() != null ? override.getVendor() : defaultVendor);
            definition.setModelName(override.getModelName() != null ? override.getModelName() : kind.getName());
            definition.setBaseUrl(override.getBaseUrl() != null ? override.getBaseUrl() : defaultBaseUrl);
            definition.setApiKey(override.getApiKey() != null ? override.getApiKey() : defaultApiKey);
            definition.setTimeout(override.getTimeout() != null ? override.getTimeout() : defaultTimeout);
            return definition;
        }
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
        private boolean useDefaultIfNoOverride = true;


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

        public boolean isUseDefaultIfNoOverride() {
            return useDefaultIfNoOverride;
        }

        public void setUseDefaultIfNoOverride(boolean useDefaultIfNoOverride) {
            this.useDefaultIfNoOverride = useDefaultIfNoOverride;
        }


    }
}

