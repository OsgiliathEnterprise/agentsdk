package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.LlmHeader;
import net.osgiliath.agentsdk.common.parsing.McpHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentHeadersTest {

    @Test
    void shouldKeepAgentHeaderInheritanceChainCompatibleWithMarkdownHeader() {
        assertThat(MarkdownHeader.class).isAssignableFrom(AgentHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentArgumentHintHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentUserInvokableHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentDisableModelInvocationHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentSubagentsHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentHandoffsHeader.class);
        assertThat(AgentHeader.class).isAssignableFrom(AgentSkillsHeader.class);
        // common types flow through MarkdownHeader
        assertThat(MarkdownHeader.class).isAssignableFrom(NameHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(DescriptionHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(McpHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(LlmHeader.class);
    }

    @Test
    void shouldCreateTypedHeadersFromGenericMarkdownHeaders() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "Code Review Agent"),
            new ParsingHeader("description", "Reviews code changes"),
            new ParsingHeader("argument-hint", "[file or code to review]"),
            new ParsingHeader("tools", List.of("read", "search")),
            new ParsingHeader("llm", List.of("claude-3-5-sonnet-20241022", "gpt-4.1")),
            new ParsingHeader("user-invokable", true),
            new ParsingHeader("disable-model-invocation", false),
            new ParsingHeader("agents", List.of("backend", "frontend")),
            new ParsingHeader("handoffs", List.of(new AgentHandoff("Backend", "backend", "Continue on backend", false))),
            new ParsingHeader("skills", List.of("Security Analysis", "Code Quality Assessment"))
        );

        AgentHeaders parsed = AgentHeaders.from(headers);

        assertThat(parsed.name().value()).isEqualTo("Code Review Agent");
        assertThat(parsed.description().value()).isEqualTo("Reviews code changes");
        assertThat(parsed.argumentHint().value()).isEqualTo("[file or code to review]");
        assertThat(parsed.mcp().value()).containsExactly("read", "search");
        assertThat(parsed.llm().value()).containsExactly("claude-3-5-sonnet-20241022", "gpt-4.1");
        assertThat(parsed.userInvokable().value()).isTrue();
        assertThat(parsed.disableModelInvocation().value()).isFalse();
        assertThat(parsed.subagents().value()).containsExactly("backend", "frontend");
        assertThat(parsed.handoffs().value())
            .containsExactly(new AgentHandoff("Backend", "backend", "Continue on backend", false));
        assertThat(parsed.skills().value()).containsExactly("Security Analysis", "Code Quality Assessment");
    }

    @Test
    void shouldSupportMcpKeyWhenParsingRawHeaders() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "mcp", List.of("read", "search")
        ));

        assertThat(parsed.mcp().value()).containsExactly("read", "search");
        assertThat(parsed.header("mcp")).contains(List.of("read", "search"));
        assertThat(parsed.header("tools")).contains(List.of("read", "search"));
    }

    @Test
    void shouldPreferMcpWhenMcpAndToolsAreBothPresent() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "mcp", List.of("read"),
            "tools", List.of("search")
        ));

        assertThat(parsed.mcp().value()).containsExactly("read");
        assertThat(parsed.header("tools")).contains(List.of("read"));
    }

    @Test
    void shouldSupportAgentsAliasWhenParsingRawHeaders() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "agents", List.of("backend", "frontend")
        ));

        assertThat(parsed.subagents().value()).containsExactly("backend", "frontend");
        assertThat(parsed.header("agents")).contains(List.of("backend", "frontend"));
        assertThat(parsed.header("subagents")).contains(List.of("backend", "frontend"));
    }

    @Test
    void shouldSupportModelAliasWhenParsingRawHeaders() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "model", List.of("gpt-4.1", "claude-3-5-sonnet-20241022")
        ));

        assertThat(parsed.llm().value()).containsExactly("gpt-4.1", "claude-3-5-sonnet-20241022");
        assertThat(parsed.header("llm")).contains(List.of("gpt-4.1", "claude-3-5-sonnet-20241022"));
        assertThat(parsed.header("model")).contains(List.of("gpt-4.1", "claude-3-5-sonnet-20241022"));
    }

    @Test
    void shouldPreferLlmWhenLlmAndModelAreBothPresent() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "llm", List.of("claude-3-5-sonnet-20241022"),
            "model", List.of("gpt-4.1")
        ));

        assertThat(parsed.llm().value()).containsExactly("claude-3-5-sonnet-20241022");
        assertThat(parsed.header("model")).contains(List.of("claude-3-5-sonnet-20241022"));
    }

    @Test
    void shouldParseStructuredHandoffsFromRawHeaders() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "handoffs", List.of(Map.of(
                "label", "Backend",
                "agent", "backend",
                "prompt", "Continue on backend",
                "send", true
            ))
        ));

        assertThat(parsed.handoffs().value())
            .containsExactly(new AgentHandoff("Backend", "backend", "Continue on backend", true));
    }

    @Test
    void shouldParseStructuredHandoffsFromMarkdownTextUsingYamlParser() {
        String markdown = """
            ---
            name: "Code Review Agent"
            description: "Reviews code changes"
            handoffs:
              - label: "Backend"
                agent: "backend"
                prompt: "Continue on backend #1"
                send: true
            ---
            # Code Review Agent
            """;

        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "text", markdown
        ));

        assertThat(parsed.handoffs().value())
            .containsExactly(new AgentHandoff("Backend", "backend", "Continue on backend #1", true));
    }

    @Test
    void shouldIgnoreLegacyFlatHandoffsPayloads() {
        AgentHeaders parsed = AgentHeaders.fromRawHeaders(Map.of(
            "name", "Code Review Agent",
            "description", "Reviews code changes",
            "handoffs", List.of(
                "- label: Backend",
                "agent: backend",
                "prompt: Continue on backend",
                "send: true"
            )
        ));

        assertThat(parsed.handoffs().value()).isEmpty();
    }

    @Test
    void shouldRemainCompatibleWithMarkdownHeadersAccessors() {
        AgentHeaders headers = new AgentHeaders(
            "Code Review Agent",
            "Reviews code changes",
            "[file or code to review]",
            List.of("read", "search"),
            List.of("claude-3-5-sonnet-20241022"),
            true,
            false,
            List.of("backend"),
            List.of(new AgentHandoff("Backend", "backend", "Continue on backend", false)),
            List.of("Security Analysis")
        );

        assertThat(headers.headerKeys()).containsExactly(
            "name",
            "description",
            "argument-hint",
            "mcp",
            "llm",
            "user-invokable",
            "disable-model-invocation",
            "subagents",
            "handoffs",
            "skills"
        );
        assertThat(headers.header("name")).contains("Code Review Agent");
        assertThat(headers.header("mcp")).contains(List.of("read", "search"));
        assertThat(headers.header("tools")).contains(List.of("read", "search"));
        assertThat(headers.header("model")).contains(List.of("claude-3-5-sonnet-20241022"));
        assertThat(headers.header("unknown")).isEmpty();
    }

    @Test
    void shouldRejectMissingName() {
        assertThatThrownBy(() -> AgentHeaders.from(List.of(
            new ParsingHeader("description", "Reviews code changes")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    @Test
    void shouldRejectMissingDescription() {
        assertThatThrownBy(() -> AgentHeaders.from(List.of(
            new ParsingHeader("name", "Code Review Agent")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
    }
}
