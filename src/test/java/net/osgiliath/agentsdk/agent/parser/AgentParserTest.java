package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.data.message.SystemMessage;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.parser.SkillAsset;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.skills.parser.SkillParserImpl;
import net.osgiliath.agentsdk.skills.parser.SkillRenderer;
import net.osgiliath.agentsdk.skills.parser.SkillRendererImpl;
import net.osgiliath.agentsdk.skills.parser.SkillScriptCommand;
import net.osgiliath.agentsdk.skills.parser.SkillTemplate;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import net.osgiliath.agentsdk.skills.resolver.SkillResolverImpl;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParserImpl;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentParserTest {

    private static final Path SAMPLE_AGENT_FILE = Path.of(
            "src/test/resources/dataset/markdown/skills/sample-agent/sample-agent.md");

    private static final Path SIMPLE_AGENT_FILE = Path.of(
            "src/test/resources/dataset/markdown/agents/agent1.md");

    private static final Path SKILL_BACKED_AGENT_FILE = Path.of(
            "src/test/resources/dataset/markdown/agents/agent-with-sample-skill.md");

    private AgentParser agentParser;
    private SkillResolver skillResolver;
    private SkillRenderer skillRenderer;

    @BeforeEach
    void setUp() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        skillResolver = mock(SkillResolver.class);
        skillRenderer = mock(SkillRenderer.class);
        agentParser = new AgentParserImpl(markdownParser, skillResolver, skillRenderer);
    }

    @Test
    void shouldParseTypedHeaders() {
        when(skillResolver.resolveSkills(any())).thenReturn(List.of());
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.headers().name()).isEqualTo(new NameHeader("Code Review Agent"));
        assertThat(agent.headers().description())
                .isEqualTo(new DescriptionHeader("Reviews code changes for quality, security vulnerabilities, performance issues, and adherence to best practices"));
        assertThat(agent.headers().argumentHint()).isEqualTo(new AgentArgumentHintHeader("[file or code to review]"));
        assertThat(agent.headers().mcp().value()).containsExactly("read", "search");
        assertThat(agent.headers().llm().value()).containsExactly(LLMS_KIND.MINI, LLMS_KIND.THINKING);
        assertThat(agent.headers().userInvokable().value()).isTrue();
        assertThat(agent.headers().disableModelInvocation().value()).isFalse();
        assertThat(agent.headers().subagents().value()).containsExactly("subagent-1", "subagent-2", "...");
        assertThat(agent.headers().skills().value())
                .containsExactly("implements_features_file");
    }

    @Test
    void shouldParseStructuredHandoffs() {
        when(skillResolver.resolveSkills(any())).thenReturn(List.of());
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.getHandoffs()).containsExactly(
                new AgentHandoff("Hand off to Backend", "subagent-1", "Continue working on the backend for this task.", false)
        );
    }

    @Test
    void shouldExposeConvenienceAccessors() {
        Skill resolvedSkill = mock(Skill.class);
        when(skillResolver.resolveSkills(any())).thenReturn(List.of(resolvedSkill));
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.getName()).isEqualTo("Code Review Agent");
        assertThat(agent.getDescription()).contains("Reviews code changes");
        assertThat(agent.getArgumentHint()).isEqualTo("[file or code to review]");
        assertThat(agent.getTools()).containsExactly("read", "search");
        assertThat(agent.getLlms()).containsExactly(LLMS_KIND.MINI, LLMS_KIND.THINKING);
        assertThat(agent.isUserInvokable()).isTrue();
        assertThat(agent.isModelInvocationDisabled()).isFalse();
        assertThat(agent.getSubagents()).containsExactly("subagent-1", "subagent-2", "...");
        assertThat(agent.getSkillsName()).contains("implements_features_file");
        assertThat(agent.getSkills()).containsExactly(resolvedSkill);
    }

    @Test
    void shouldParseContentIntoTypedSectionsWrapper() {
        when(skillResolver.resolveSkills(any())).thenReturn(List.of());
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.getContent().sections()).isEqualTo(agent.getLevel1Content());
        List<String> rootTitles = agent.getLevel1Content().stream()
                .map(MarkdownSection::getTitle)
                .toList();
        List<String> subsectionTitles = agent.getLevel1Content().getFirst().getSubSections().stream()
                .map(MarkdownSection::getTitle)
                .toList();

        assertThat(rootTitles).containsExactly("Code Review Agent");
        assertThat(subsectionTitles).contains("Responsibilities", "Rules (mandatory read)", "Workflow", "Appendices");
    }

    @Test
    void shouldParseLegacySimpleAgentHeaders() {
        when(skillResolver.resolveSkills(any())).thenReturn(List.of());
        Agent agent = agentParser.getAgent(SIMPLE_AGENT_FILE);

        assertThat(agent.getName()).isEqualTo("Cloud Engineer");
        assertThat(agent.getDescription()).isEqualTo("Implements cloud scripts");
        assertThat(agent.getTools()).contains("insert_edit_into_file", "run_in_terminal", "sequentialthinking");
        assertThat(agent.getLlms()).containsExactly(LLMS_KIND.MINI);
        assertThat(agent.getSubagents()).isEmpty();
        assertThat(agent.getHandoffs()).isEmpty();
        assertThat(agent.getSkillsName()).isEmpty();
    }

    @Test
    void shouldBuildSystemPromptContainingAgentContentAndRenderedSkills() {
        Skill mockSkill = mock(Skill.class);
        when(skillResolver.resolveSkills(any())).thenReturn(List.of(mockSkill));
        when(skillRenderer.renderFlat(mockSkill)).thenReturn("## Rendered Skill Section");
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        SystemMessage systemMessage = agentParser.getSystemPrompt(agent);

        assertThat(systemMessage.text()).contains("Code Review Agent");
        assertThat(systemMessage.text()).contains("## Rendered Skill Section");
    }

    @Test
    void shouldBuildSystemPromptTextAndDocumentFromSharedLogic() {
        Skill mockSkill = mock(Skill.class);
        when(skillResolver.resolveSkills(any())).thenReturn(List.of(mockSkill));
        when(skillRenderer.renderFlat(mockSkill)).thenReturn("## Rendered Skill Section");
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        SystemMessage systemPrompt = agentParser.getSystemPrompt(agent);
        assertThat(systemPrompt.text()).contains("Code Review Agent");
        assertThat(systemPrompt.text()).contains("## Rendered Skill Section");
        verify(skillResolver, atLeastOnce()).resolveSkills(any());
    }

    @Test
    void shouldAvoidDuplicatingPromptBlocksWhenSkillRenderingMatchesAgentContent() {
        Skill mockSkill = mock(Skill.class);
        when(skillResolver.resolveSkills(any())).thenReturn(List.of(mockSkill));
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        String baseText = agentParser.getSystemPrompt(agent).text();
        when(skillRenderer.renderFlat(mockSkill)).thenReturn(baseText);

        String deduplicatedText = agentParser.getSystemPrompt(agent).text();

        assertThat(deduplicatedText).isEqualTo(baseText);
    }

    @Test
    void shouldBuildSystemPromptWithNoSkillsWhenAgentDeclaredNone() {
        when(skillResolver.resolveSkills(List.of())).thenReturn(List.of());
        Agent agent = agentParser.getAgent(SIMPLE_AGENT_FILE);

        SystemMessage systemMessage = agentParser.getSystemPrompt(agent);

        assertThat(systemMessage.text()).contains("Cloud Engineer");
    }

    @Test
    void shouldLoadResolvedSkillsAndTheirResourcesIntoTheAgent() {
        AgentParser realAgentParser = createRealAgentParser();

        Agent agent = realAgentParser.getAgent(SKILL_BACKED_AGENT_FILE);

        assertThat(agent.getSkills()).hasSize(1);

        Skill skill = agent.getSkills().iterator().next();
        assertThat(skill.getName()).isEqualTo("implements_features_file");
        assertThat(skill.getAssets()).map(SkillAsset::uri).containsExactly("assets/eval_review.html");
        assertThat(skill.getTemplates()).map(SkillTemplate::uri).containsExactly("templates/generator_template.js");
        assertThat(skill.getCommands())
                .contains(new SkillScriptCommand("./gradlew", "./gradlew scripts/build.gradle.kts ping"));
        assertThat(skill.getLevel1Content()).extracting(MarkdownSection::getTitle)
                .contains("PPTX Skill", "Instructions", "MCP Server Evaluation Guide");

        SystemMessage systemMessage = realAgentParser.getSystemPrompt(agent);

        assertThat(systemMessage.text()).contains("Skill Loaded Agent");
        assertThat(systemMessage.text()).contains("MCP Server Evaluation Guide");
        assertThat(systemMessage.text()).contains("Instructions");
        assertThat(systemMessage.text()).contains("assets/eval_review.html");
        assertThat(systemMessage.text()).contains("templates/generator_template.js");
        assertThat(systemMessage.text()).contains("./gradlew scripts/build.gradle.kts ping");
    }

    @Test
    void shouldThrowWhenAgentFileDoesNotExist() {
        Path nonExistent = Path.of("src/test/resources/dataset/markdown/agents/no-such-agent.md");

        assertThatThrownBy(() -> agentParser.getAgent(nonExistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenAgentFileIsNull() {
        assertThatThrownBy(() -> agentParser.getAgent(null))
                .isInstanceOf(NullPointerException.class);
    }

    private AgentParser createRealAgentParser() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        SkillParser skillParser = new SkillParserImpl(markdownParser, commonmarkParser);

        CodepromptConfiguration config = new CodepromptConfiguration();
        config.getAgent().setSkillFolders(List.of("classpath:dataset/markdown/skills/"));

        SkillResolver resolver = new SkillResolverImpl(
                config,
                skillParser,
                new PathMatchingResourcePatternResolver());
        SkillRenderer renderer = new SkillRendererImpl();

        return new AgentParserImpl(markdownParser, resolver, renderer);
    }
}



