package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import net.osgiliath.agentsdk.llm.ChatModelFactory;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatModelConfigurationTest {

    @Mock
    private CodepromptConfiguration codepromptConfiguration;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private LlmKindModelProperties llmKindModelProperties;

    @Mock
    private LlmKindModelProperties.ModelDefinition modelDefinition;

    @Mock
    private ConfigurableListableBeanFactory beanFactory;

    @Mock
    private ChatModel mockChatModel;

    @Mock
    private StreamingChatModel mockStreamingChatModel;

    private ChatModelConfiguration chatModelConfiguration;

    @BeforeEach
    void setUp() {
        when(codepromptConfiguration.getLlms()).thenReturn(llmKindModelProperties);
        chatModelConfiguration = new ChatModelConfiguration(codepromptConfiguration, chatModelFactory);
    }

    @Test
    void shouldCreateThinkingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.THINKING)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.thinkingChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateNanoChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.NANO)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.nanoChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMiniChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MINI)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.miniChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMediumChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MEDIUM)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.mediumChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateBigChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.BIG)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.bigChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateSuperChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.SUPER)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.superChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMaxiChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MAXI)).thenReturn(modelDefinition);
        when(chatModelFactory.createChatModel(modelDefinition)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.maxiChatModel();

        assertEquals(mockChatModel, result);
        verify(chatModelFactory).createChatModel(modelDefinition);
    }

    @Test
    void shouldCreateThinkingStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.THINKING)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.thinkingStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateNanoStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.NANO)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.nanoStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMiniStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MINI)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.miniStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMediumStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MEDIUM)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.mediumStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateBigStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.BIG)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.bigStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateSuperStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.SUPER)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.superStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreateMaxiStreamingChatModel() {
        when(llmKindModelProperties.resolve(LLMS_KIND.MAXI)).thenReturn(modelDefinition);
        when(chatModelFactory.createStreamingChatModel(modelDefinition)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.maxiStreamingChatModel();

        assertEquals(mockStreamingChatModel, result);
        verify(chatModelFactory).createStreamingChatModel(modelDefinition);
    }

    @Test
    void shouldCreatePrimaryChatModel() {
        when(llmKindModelProperties.getPrimaryKind()).thenReturn(LLMS_KIND.MINI);
        when(beanFactory.getBean("miniChatModel", ChatModel.class)).thenReturn(mockChatModel);

        ChatModel result = chatModelConfiguration.primaryChatModel(beanFactory);

        assertEquals(mockChatModel, result);
        verify(beanFactory).getBean("miniChatModel", ChatModel.class);
    }

    @Test
    void shouldCreatePrimaryStreamingChatModel() {
        when(llmKindModelProperties.getPrimaryKind()).thenReturn(LLMS_KIND.BIG);
        when(beanFactory.getBean("bigStreamingChatModel", StreamingChatModel.class)).thenReturn(mockStreamingChatModel);

        StreamingChatModel result = chatModelConfiguration.primaryStreamingChatModel(beanFactory);

        assertEquals(mockStreamingChatModel, result);
        verify(beanFactory).getBean("bigStreamingChatModel", StreamingChatModel.class);
    }
}
