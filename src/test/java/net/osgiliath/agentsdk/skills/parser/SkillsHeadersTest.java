package net.osgiliath.agentsdk.skills.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillsHeadersTest {

    @Test
    void shouldCreateTypedHeadersFromGenericMarkdownHeaders() {
        List<MarkdownHeader> headers = List.of(
            new SkillHeader("name", "implements features file"),
            new SkillHeader("description", "gherkin writer"),
            new SkillHeader("dependencies", "python>=3.8, pandas>=1.5.0, matplotlib"),
            new SkillHeader("mcp", List.of("server-name-1", "server-name-2")),
            new SkillHeader("llm", List.of("claude-3-5-sonnet-20241022"))
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.name()).isEqualTo("implements features file");
        assertThat(parsed.description()).isEqualTo("gherkin writer");
        assertThat(parsed.dependencies()).containsExactly("python>=3.8", "pandas>=1.5.0", "matplotlib");
        assertThat(parsed.mcp()).containsExactly("server-name-1", "server-name-2");
        assertThat(parsed.llm()).containsExactly("claude-3-5-sonnet-20241022");
    }

    @Test
    void shouldDefaultOptionalHeadersToEmptyListWhenAbsent() {
        List<MarkdownHeader> headers = List.of(
            new SkillHeader("name", "implements features file"),
            new SkillHeader("description", "gherkin writer")
        );

        SkillsHeaders parsed = SkillsHeaders.from(headers);

        assertThat(parsed.dependencies()).isEmpty();
        assertThat(parsed.mcp()).isEmpty();
        assertThat(parsed.llm()).isEmpty();
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
            new SkillHeader("description", "gherkin writer")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    @Test
    void shouldRejectMissingDescription() {
        assertThatThrownBy(() -> SkillsHeaders.from(List.of(
            new SkillHeader("name", "implements features file")
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
    }
}
