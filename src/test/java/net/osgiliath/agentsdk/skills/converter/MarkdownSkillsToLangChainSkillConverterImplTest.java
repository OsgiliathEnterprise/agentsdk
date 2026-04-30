package net.osgiliath.agentsdk.skills.converter;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.skills.Skills;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.mcp.AliasAwareToolProviderComposer;
import net.osgiliath.agentsdk.mcp.McpToolAliasResolver;
import net.osgiliath.agentsdk.mcp.McpToolAliasResolverImpl;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.skills.parser.SkillParserImpl;
import net.osgiliath.agentsdk.skills.parser.SkillRenderer;
import net.osgiliath.agentsdk.skills.parser.SkillRendererImpl;
import net.osgiliath.agentsdk.skills.parser.SkillsHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParserImpl;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolverImpl;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownSkillsToLangChainSkillConverterImplTest {

    private static final String SKILL_FILE =
            "classpath:/dataset/markdown/skills/implements_features_file/SKILL.md";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    private SkillParser skillParser;
    private SkillRenderer skillRenderer;
    private MarkdownSkillsToLangChainSkillConverter converter;

    @BeforeEach
    void setUp() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        ResourceLocationResolver resourceLocationResolver =
                new ResourceLocationResolverImpl(new PathMatchingResourcePatternResolver());
        skillParser = new SkillParserImpl(markdownParser, commonmarkParser, resourceLocationResolver,
                new net.osgiliath.agentsdk.skills.assertions.SkillAssertionSetParser(resourceLocationResolver, new com.fasterxml.jackson.databind.ObjectMapper()));
        skillRenderer = new SkillRendererImpl();

        ToolProvider fullToolProvider = request -> ToolProviderResult.builder()
                .add(spec("inventory_fetch"), (toolRequest, memoryId) -> "inventory-ok")
                .add(spec("server-name-2"), (toolRequest, memoryId) -> "server-2-ok")
                .add(spec("not-declared"), (toolRequest, memoryId) -> "ignored")
                .build();

        CodepromptConfiguration properties = new CodepromptConfiguration();
        properties.getMcp().getTools().setAliases(Map.of(
                "server-name-1", List.of("inventory_fetch", "server-name-1")
        ));
        McpToolAliasResolver aliasResolver = new McpToolAliasResolverImpl(properties);
        AliasAwareToolProviderComposer aliasAwareToolProviderComposer = new AliasAwareToolProviderComposer(aliasResolver);

        converter = new MarkdownSkillsToLangChainSkillConverterImpl(
                skillRenderer,
                fullToolProvider,
                aliasAwareToolProviderComposer
        );
    }

    @Test
    void shouldConvertParsedMarkdownSkillIntoLangChainSkill() {
        Skill markdownSkill = skillParser.getSkill(resourceResolver.getResource(SKILL_FILE));

        dev.langchain4j.skills.Skill langChainSkill = converter.convert(markdownSkill);

        assertThat(langChainSkill.name()).isEqualTo(markdownSkill.getName());
        assertThat(langChainSkill.description()).isEqualTo(markdownSkill.getDescription());
        assertThat(langChainSkill.content()).isEqualTo(skillRenderer.renderFlat(markdownSkill));
        assertThat(langChainSkill.content())
                .contains("## Assets")
                .contains("templates/generator_template.js")
                .contains("./gradlew scripts/build.gradle.kts ping");
        assertThat(langChainSkill.resources()).isEmpty();

        assertThat(langChainSkill.toolProviders()).hasSize(1);

        ToolProviderResult tools = langChainSkill.toolProviders().getFirst()
                .provideTools(toolProviderRequest());
        assertThat(tools.toolSpecificationByName("server-name-1")).isNotNull();
        assertThat(tools.toolSpecificationByName("server-name-2")).isNotNull();
        assertThat(tools.toolSpecificationByName("not-declared")).isNull();

        String aliasExecution = tools.toolExecutorByName("server-name-1")
                .execute(toolRequest("server-name-1"), "memory-id");
        assertThat(aliasExecution).isEqualTo("inventory-ok");
    }

    @Test
    void shouldIntegrateWithLangChainSkillsContainer() {
        Skill markdownSkill = skillParser.getSkill(resourceResolver.getResource(SKILL_FILE));

        Skills skills = Skills.from(converter.convert(markdownSkill));

        assertThat(skills.formatAvailableSkills())
                .contains("<name>implements_features_file</name>")
                .contains("<description>You&apos;re a Gherkin scenario writer.");
    }

    @Test
    void shouldRejectNullMarkdownSkill() {
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("markdownSkill must not be null");
    }

    @Test
    void shouldNotAttachToolProvidersWhenSkillDeclaresNoTools() {
        Skill markdownSkill = new Skill(
                new SkillsHeaders("no-tools-skill", "Skill without MCP tools", List.of(), List.of(), List.of()),
                List.of(),
                List.of(),
                List.of(),
                new MarkdownContentSections(List.of()),
                List.of()
        );

        dev.langchain4j.skills.Skill langChainSkill = converter.convert(markdownSkill);

        assertThat(langChainSkill.name()).isEqualTo("no-tools-skill");
        assertThat(langChainSkill.description()).isEqualTo("Skill without MCP tools");
        assertThat(langChainSkill.content()).isEqualTo(skillRenderer.renderFlat(markdownSkill));
        assertThat(langChainSkill.toolProviders()).isEmpty();
        assertThat(langChainSkill.resources()).isEmpty();
    }

    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder()
                .name(name)
                .description("tool " + name)
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private static ToolExecutionRequest toolRequest(String name) {
        return ToolExecutionRequest.builder()
                .id("id-1")
                .name(name)
                .arguments("{}")
                .build();
    }

    private static dev.langchain4j.service.tool.ToolProviderRequest toolProviderRequest() {
        return dev.langchain4j.service.tool.ToolProviderRequest.builder()
                .userMessage(UserMessage.from("test-tools"))
                .invocationContext(InvocationContext.builder()
                        .chatMemoryId("test-memory")
                        .invocationParameters(new InvocationParameters())
                        .timestampNow()
                        .build())
                .build();
    }
}
