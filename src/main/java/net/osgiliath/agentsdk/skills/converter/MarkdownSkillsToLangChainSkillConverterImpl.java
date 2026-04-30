package net.osgiliath.agentsdk.skills.converter;

import dev.langchain4j.skills.Skill;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.configuration.LangChain4jConfig;
import net.osgiliath.agentsdk.mcp.AliasAwareToolProviderComposer;
import net.osgiliath.agentsdk.skills.parser.SkillRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class MarkdownSkillsToLangChainSkillConverterImpl implements MarkdownSkillsToLangChainSkillConverter {

    private final SkillRenderer skillRenderer;
    private final ToolProvider fullToolProvider;
    private final AliasAwareToolProviderComposer aliasAwareToolProviderComposer;

    public MarkdownSkillsToLangChainSkillConverterImpl(
            SkillRenderer skillRenderer,
            @Qualifier(LangChain4jConfig.TOOL_PROVIDER_FULL) ToolProvider fullToolProvider,
            AliasAwareToolProviderComposer aliasAwareToolProviderComposer) {
        this.skillRenderer = Objects.requireNonNull(skillRenderer, "skillRenderer must not be null");
        this.fullToolProvider = Objects.requireNonNull(fullToolProvider, "fullToolProvider must not be null");
        this.aliasAwareToolProviderComposer = Objects.requireNonNull(aliasAwareToolProviderComposer, "aliasAwareToolProviderComposer must not be null");
    }

    @Override
    public Skill convert(net.osgiliath.agentsdk.skills.parser.Skill markdownSkill) {
        Objects.requireNonNull(markdownSkill, "markdownSkill must not be null");
        List<String> declaredToolNames = markdownSkill.getMcps().stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();

        var builder = Skill.builder()
                .name(markdownSkill.getName())
                .description(markdownSkill.getDescription())
                .content(skillRenderer.renderFlat(markdownSkill));

        if (!declaredToolNames.isEmpty()) {
            builder.toolProviders(filteredToolProvider(declaredToolNames));
        }

        return builder.build();
    }

    private ToolProvider filteredToolProvider(List<String> declaredToolNames) {
        Objects.requireNonNull(declaredToolNames, "declaredToolNames must not be null");
        if (declaredToolNames.isEmpty()) {
            throw new IllegalArgumentException("declaredToolNames must not be empty");
        }
        return toolProviderRequest -> {
            ToolProviderRequest request = toolProviderRequest == null
                    ? ToolProviderRequest.builder()
                    .userMessage(UserMessage.from("resolve-skill-tools"))
                    .invocationContext(InvocationContext.builder()
                            .chatMemoryId("skill-converter")
                            .invocationParameters(new InvocationParameters())
                            .timestampNow()
                            .build())
                    .build()
                    : toolProviderRequest;
            ToolProviderResult fullResult = fullToolProvider.provideTools(request);
            return aliasAwareToolProviderComposer.compose(fullResult, declaredToolNames);
        };
    }
}
