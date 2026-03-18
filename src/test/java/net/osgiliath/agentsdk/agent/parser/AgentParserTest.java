package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParserImpl;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentParserTest {

    private static final Path SAMPLE_AGENT_FILE = Path.of(
        "src/test/resources/dataset/markdown/skills/sample-agent/sample-agent.md");

    private static final Path SIMPLE_AGENT_FILE = Path.of(
        "src/test/resources/dataset/markdown/agents/agent1.md");

    private AgentParser agentParser;

    @BeforeEach
    void setUp() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        agentParser = new AgentParserImpl(markdownParser);
    }

    @Test
    void shouldParseTypedHeaders() {
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.headers().name()).isEqualTo(new NameHeader("Code Review Agent"));
        assertThat(agent.headers().description())
            .isEqualTo(new DescriptionHeader("Reviews code changes for quality, security vulnerabilities, performance issues, and adherence to best practices"));
        assertThat(agent.headers().argumentHint()).isEqualTo(new AgentArgumentHintHeader("[file or code to review]"));
        assertThat(agent.headers().mcp().value()).containsExactly("read", "search");
        assertThat(agent.headers().llm().value()).containsExactly("claude-3-5-sonnet-20241022", "gpt-4.1");
        assertThat(agent.headers().userInvokable().value()).isTrue();
        assertThat(agent.headers().disableModelInvocation().value()).isFalse();
        assertThat(agent.headers().subagents().value()).containsExactly("subagent-1", "subagent-2", "...");
        assertThat(agent.headers().skills().value())
            .containsExactly("Security Analysis", "Code Quality Assessment", "Performance Optimization", "Best Practices Enforcement");
    }

    @Test
    void shouldParseStructuredHandoffs() {
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.getHandoffs()).containsExactly(
            new AgentHandoff("Hand off to Backend", "subagent-1", "Continue working on the backend for this task.", false)
        );
    }

    @Test
    void shouldExposeConvenienceAccessors() {
        Agent agent = agentParser.getAgent(SAMPLE_AGENT_FILE);

        assertThat(agent.getName()).isEqualTo("Code Review Agent");
        assertThat(agent.getDescription()).contains("Reviews code changes");
        assertThat(agent.getArgumentHint()).isEqualTo("[file or code to review]");
        assertThat(agent.getTools()).containsExactly("read", "search");
        assertThat(agent.getLlms()).containsExactly("claude-3-5-sonnet-20241022", "gpt-4.1");
        assertThat(agent.isUserInvokable()).isTrue();
        assertThat(agent.isModelInvocationDisabled()).isFalse();
        assertThat(agent.getSubagents()).containsExactly("subagent-1", "subagent-2", "...");
        assertThat(agent.getSkills()).contains("Security Analysis");
    }

    @Test
    void shouldParseContentIntoTypedSectionsWrapper() {
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
        Agent agent = agentParser.getAgent(SIMPLE_AGENT_FILE);

        assertThat(agent.getName()).isEqualTo("Cloud Engineer");
        assertThat(agent.getDescription()).isEqualTo("Implements cloud scripts");
        assertThat(agent.getTools()).contains("insert_edit_into_file", "run_in_terminal", "sequentialthinking");
        assertThat(agent.getLlms()).containsExactly("claude-3-5-sonnet-20241022");
        assertThat(agent.getSubagents()).isEmpty();
        assertThat(agent.getHandoffs()).isEmpty();
        assertThat(agent.getSkills()).isEmpty();
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
}



