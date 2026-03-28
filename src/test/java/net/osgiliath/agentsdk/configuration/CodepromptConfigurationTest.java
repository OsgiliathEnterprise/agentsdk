package net.osgiliath.agentsdk.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CodepromptConfigurationTest {

    @Test
    void shouldExposeDefaultLlmPropertiesWhenNothingIsBound() {
        CodepromptConfiguration configuration = new CodepromptConfiguration();

        assertNotNull(configuration.getLlmProperties());
    }

    @Test
    void shouldFallbackToDefaultLlmPropertiesWhenSetterReceivesNull() {
        CodepromptConfiguration configuration = new CodepromptConfiguration();

        configuration.setLlmProperties(null);

        assertNotNull(configuration.getLlmProperties());
    }
}

