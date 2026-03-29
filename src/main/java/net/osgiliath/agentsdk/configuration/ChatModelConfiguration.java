package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import net.osgiliath.agentsdk.llm.ChatModelFactory;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration that builds one ChatModel and one StreamingChatModel bean for each LLMS_KIND.
 */
@Configuration
@Profile("!github")
public class ChatModelConfiguration {

    private final CodepromptConfiguration codepromptConfiguration;
    private final ChatModelFactory chatModelFactory;

    public ChatModelConfiguration(CodepromptConfiguration codepromptConfiguration,
                                  ChatModelFactory chatModelFactory) {
        this.codepromptConfiguration = codepromptConfiguration;
        this.chatModelFactory = chatModelFactory;
    }

    @Bean("thinkingChatModel")
    public ChatModel thinkingChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.THINKING));
    }

    @Bean("nanoChatModel")
    public ChatModel nanoChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.NANO));
    }

    @Bean("miniChatModel")
    public ChatModel miniChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MINI));
    }

    @Bean("mediumChatModel")
    public ChatModel mediumChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MEDIUM));
    }

    @Bean("bigChatModel")
    public ChatModel bigChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.BIG));
    }

    @Bean("superChatModel")
    public ChatModel superChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.SUPER));
    }

    @Bean("maxiChatModel")
    public ChatModel maxiChatModel() {
        return chatModelFactory.createChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MAXI));
    }

    @Bean("thinkingStreamingChatModel")
    public StreamingChatModel thinkingStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.THINKING));
    }

    @Bean("nanoStreamingChatModel")
    public StreamingChatModel nanoStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.NANO));
    }

    @Bean("miniStreamingChatModel")
    public StreamingChatModel miniStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MINI));
    }

    @Bean("mediumStreamingChatModel")
    public StreamingChatModel mediumStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MEDIUM));
    }

    @Bean("bigStreamingChatModel")
    public StreamingChatModel bigStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.BIG));
    }

    @Bean("superStreamingChatModel")
    public StreamingChatModel superStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.SUPER));
    }

    @Bean("maxiStreamingChatModel")
    public StreamingChatModel maxiStreamingChatModel() {
        return chatModelFactory.createStreamingChatModel(codepromptConfiguration.getLlms().resolve(LLMS_KIND.MAXI));
    }

    @Bean("primaryChatModel")
    @Primary
    public ChatModel primaryChatModel(ConfigurableListableBeanFactory beanFactory) {
        return beanFactory.getBean(codepromptConfiguration.getLlms().getPrimaryKind().chatBeanName(), ChatModel.class);
    }

    @Bean("primaryStreamingChatModel")
    @Primary
    public StreamingChatModel primaryStreamingChatModel(ConfigurableListableBeanFactory beanFactory) {
        return beanFactory.getBean(codepromptConfiguration.getLlms().getPrimaryKind().streamingBeanName(), StreamingChatModel.class);
    }
}
