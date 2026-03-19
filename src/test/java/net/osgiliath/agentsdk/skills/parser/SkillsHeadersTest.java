package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.common.parsing.DescriptionHeader;
import net.osgiliath.agentsdk.common.parsing.LlmHeader;
import net.osgiliath.agentsdk.common.parsing.McpHeader;
import net.osgiliath.agentsdk.common.parsing.NameHeader;
import net.osgiliath.agentsdk.common.parsing.ParsingHeader;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillsHeadersTest {

    @Test
    void shouldKeepSkillHeaderInheritanceChainCompatibleWithMarkdownHeader() {
        assertThat(MarkdownHeader.class).isAssignableFrom(SkillHeader.class);
        assertThat(SkillHeader.class).isAssignableFrom(SkillDependenciesHeader.class);
        // common types flow through MarkdownHeader
        assertThat(MarkdownHeader.class).isAssignableFrom(NameHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(DescriptionHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(McpHeader.class);
        assertThat(MarkdownHeader.class).isAssignableFrom(LlmHeader.class);
    }

    @Test
    void shouldCreateTypedHeadersFromGenericMarkdownHeaders() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "implements features file"),
            new ParsingHeader("description", "gherkin writer"),
            new ParsingHeader("dependencies", "python>=3.8, pandas>=1.5.0, matplotlib"),
            new ParsingHeader("mcp", List.of("server-name-1", "server-name-2")),
            new ParsingHeader("llm", List.of("claude-3-5-sonnet-20241022"))
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.name().value()).isEqualTo("implements features file");
        assertThat(parsed.description().value()).isEqualTo("gherkin writer");
        assertThat(parsed.dependencies().value()).containsExactly("python>=3.8", "pandas>=1.5.0", "matplotlib");
        assertThat(parsed.mcp().value()).containsExactly("server-name-1", "server-name-2");
        assertThat(parsed.llm().value()).containsExactly("claude-3-5-sonnet-20241022");
    }

    @Test
    void shouldSupportToolsAliasForMcp() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "implements features file"),
            new ParsingHeader("description", "gherkin writer"),
            new ParsingHeader("tools", List.of("server-name-1"))
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.mcp().value()).containsExactly("server-name-1");
        assertThat(parsed.header("mcp")).contains(List.of("server-name-1"));
        assertThat(parsed.header("tools")).contains(List.of("server-name-1"));
    }

    @Test
    void shouldSupportModelAliasForLlm() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "implements features file"),
            new ParsingHeader("description", "gherkin writer"),
            new ParsingHeader("model", List.of("gpt-4.1"))
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.llm().value()).containsExactly("gpt-4.1");
        assertThat(parsed.header("llm")).contains(List.of("gpt-4.1"));
        assertThat(parsed.header("model")).contains(List.of("gpt-4.1"));
    }

    @Test
    void shouldPreferLlmWhenLlmAndModelAreBothPresent() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "implements features file"),
            new ParsingHeader("description", "gherkin writer"),
            new ParsingHeader("llm", List.of("claude-3-5-sonnet-20241022")),
            new ParsingHeader("model", List.of("gpt-4.1"))
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.llm().value()).containsExactly("claude-3-5-sonnet-20241022");
        assertThat(parsed.header("model")).contains(List.of("claude-3-5-sonnet-20241022"));
    }

    @Test
    void shouldDefaultOptionalHeadersToEmptyListWhenAbsent() {
        List<MarkdownHeader> headers = List.of(
            new ParsingHeader("name", "implements features file"),
            new ParsingHeader("description", "gherkin writer")
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.dependencies().value()).isEmpty();
        assertThat(parsed.mcp().value()).isEmpty();
        assertThat(parsed.llm().value()).isEmpty();
    }

    @Test
    void shouldExcludeEmptyOptionalHeadersFromHeaderKeys() {
        SkillsHeaders headers = new SkillsHeaders(
            "implements features file", "gherkin writer",
            List.of(), List.of("server-name-1"), List.of()
        );

        assertThat(headers.headerKeys()).containsExactly("name", "description", "mcp");
    }

    @Test
    void shouldRemainCompatibleWithMarkdownHeadersAccessors() {
        SkillsHeaders headers = new SkillsHeaders(
            "implements features file", "gherkin writer",
            List.of("python>=3.8"), List.of("server-name-1"), List.of("claude-3-5-sonnet-20241022")
        );

        assertThat(headers.headerKeys())
            .containsExactly("name", "description", "dependencies", "mcp", "llm");
        assertThat(headers.header("name")).contains("implements features file");
        assertThat(headers.header("dependencies")).contains(List.of("python>=3.8"));
        assertThat(headers.header("model")).contains(List.of("claude-3-5-sonnet-20241022"));
        assertThat(headers.header("unknown")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForAbsentOptionalHeaderViaAccessor() {
        SkillsHeaders headers = new SkillsHeaders(
            "name", "desc", List.of(), List.of(), List.of()
        );

        assertThat(headers.header("dependencies")).isEmpty();
        assertThat(headers.header("mcp")).isEmpty();
        assertThat(headers.header("llm")).isEmpty();
    }

    @Test
    void shouldRejectMissingName() {
        assertThatThrownBy(() -> SkillsHeaders.from(List.of(
            new ParsingHeader("description", "gherkin writer")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    @Test
    void shouldRejectMissingDescription() {
        assertThatThrownBy(() -> SkillsHeaders.from(List.of(
            new ParsingHeader("name", "implements features file")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
    }
}
