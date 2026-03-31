package net.osgiliath.agentsdk.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CodepromptConfigurationTest {

    @Test
    void shouldExposeDefaultLlmPropertiesWhenNothingIsBound() {
        CodepromptConfiguration configuration = new CodepromptConfiguration();

        assertNotNull(configuration.getLlms());
    }

    @Test
    void shouldExposeDefaultAgentPropertiesWhenNothingIsBound() {
        CodepromptConfiguration configuration = new CodepromptConfiguration();

        assertNotNull(configuration.getAgent());
        assertThat(configuration.getAgent().getSkillFolders()).isEmpty();
        assertThat(configuration.getAgent().getAgentFolders()).isEmpty();
    }

    @Test
    void shouldFallbackToDefaultLlmPropertiesWhenSetterReceivesNull() {
        CodepromptConfiguration configuration = new CodepromptConfiguration();

        configuration.setLlms(null);

        assertNotNull(configuration.getLlms());
    }
}

