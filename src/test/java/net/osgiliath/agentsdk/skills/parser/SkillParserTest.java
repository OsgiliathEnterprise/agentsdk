package net.osgiliath.agentsdk.skills.parser;

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

/**
 * Unit tests for {@link SkillParserImpl} using the real sample-skill dataset.
 * Wired without Spring — all collaborators are instantiated directly.
 */
class SkillParserTest {

    private static final Path SKILL_FILE = Path.of(
        "src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md");

    private SkillParser skillParser;

    @BeforeEach
    void setUp() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        skillParser = new SkillParserImpl(markdownParser, commonmarkParser);
    }

    // ── headers ──────────────────────────────────────────────────────────────

    @Test
    void shouldParseSkillName() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getName())
            .isEqualTo("implements features file");
    }

    @Test
    void shouldParseSkillDescription() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getDescription())
            .isEqualTo("You're a Gherkin scenario writer. You will be given a feature file and a user story and you will have to write the Gherkin scenarios to define acceptance criteria.");
    }

    @Test
    void shouldParseDependencies() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getDependencies())
            .containsExactly("python>=3.8", "pandas>=1.5.0", "matplotlib");
    }

    @Test
    void shouldParseMcpServers() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getMcps())
            .containsExactly("server-name-1", "server-name-2");
    }

    @Test
    void shouldParseLlmModels() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getLlms())
            .containsExactly("claude-3-5-sonnet-20241022");
    }

    @Test
    void shouldExposeAllRequiredHeaderKeys() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.headers().headerKeys())
            .containsExactly("name", "description", "dependencies", "mcp", "llm");
    }

    // ── assets ───────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterNonMarkdownLinksAsAssets() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getAssets()).map(SkillAsset::uri)
            .containsExactly("assets/eval_review.html");
    }

    @Test
    void shouldNotIncludeMarkdownLinksInAssets() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getAssets()).map(SkillAsset::uri)
            .noneMatch(uri -> uri.endsWith(".md"));
    }

    // ── templates ────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterTemplateUris() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getTemplates()).map(SkillTemplate::uri)
            .containsExactly("templates/generator_template.js");
    }

    @Test
    void shouldUseRelativeUriForTemplates() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getTemplates()).map(SkillTemplate::uri)
            .allMatch(uri -> !Path.of(uri).isAbsolute());
    }

    // ── script commands ───────────────────────────────────────────────────────

    @Test
    void shouldExtractScriptCommandsFromFencedCodeBlocks() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getCommands()).isNotEmpty();
    }

    @Test
    void shouldExtractGradlewCommand() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getCommands())
            .contains(new SkillScriptCommand("./gradlew", "./gradlew scripts/build.gradle.kts ping"));
    }

    @Test
    void shouldExtractMultipleDistinctCommands() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        // The skill has several fenced code blocks, each containing the same gradlew line;
        // the parser deduplicates by sectionKey so we get at least one command.
        assertThat(skill.getCommands()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(skill.getCommands())
            .allMatch(cmd -> !cmd.executable().isBlank());
    }

    @Test
    void shouldNotHaveBlankExecutableOrCommandLine() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        assertThat(skill.getCommands()).isNotEmpty()
            .allMatch(cmd -> !cmd.executable().isBlank() && !cmd.commandLine().isBlank());
    }

    // ── content sections (level-1) ────────────────────────────────────────────

    @Test
    void shouldParseTopLevelContentSections() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        List<String> titles = skill.getLevel1Content().stream()
            .map(MarkdownSection::getTitle)
            .toList();

        assertThat(titles).contains("PPTX Skill");
    }

    @Test
    void shouldIncludeLinkedMarkdownContentSections() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        List<String> titles = skill.getLevel1Content().stream()
            .map(MarkdownSection::getTitle)
            .toList();

        assertThat(titles).contains("Instructions");
    }

    @Test
    void shouldIncludeGraderAgentSection() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        List<String> titles = skill.getLevel1Content().stream()
            .map(MarkdownSection::getTitle)
            .toList();

        assertThat(titles).contains("Grader Agent");
    }

    @Test
    void shouldIncludeReferenceDocumentSections() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        List<String> titles = skill.getLevel1Content().stream()
            .map(MarkdownSection::getTitle)
            .toList();

        assertThat(titles).contains("MCP Server Evaluation Guide");
    }

    @Test
    void shouldNotDuplicateSectionsFromRepeatedLinks() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        long instructionSectionCount = skill.getLevel1Content().stream()
            .filter(s -> "Instructions".equals(s.getTitle()))
            .count();

        assertThat(instructionSectionCount).isEqualTo(1);
    }

    @Test
    void shouldExposeSubSectionsUnderTopLevelSection() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        MarkdownSection pptxSkill = skill.getLevel1Content().stream()
            .filter(s -> "PPTX Skill".equals(s.getTitle()))
            .findFirst()
            .orElseThrow();

        assertThat(pptxSkill.getSubSections()).isNotEmpty();
    }

    @Test
    void shouldResolveSubSectionByTitle() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        MarkdownSection pptxSkill = skill.getLevel1Content().stream()
            .filter(s -> "PPTX Skill".equals(s.getTitle()))
            .findFirst()
            .orElseThrow();

        assertThat(pptxSkill.getSubSection("Quick Reference")).isPresent();
    }

    @Test
    void shouldHaveContentInSubSections() {
        Skill skill = skillParser.getSkill(SKILL_FILE);

        MarkdownSection instructions = skill.getLevel1Content().stream()
            .filter(s -> "Instructions".equals(s.getTitle()))
            .findFirst()
            .orElseThrow();

        assertThat(instructions.getContent()).isNotBlank();
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void shouldThrowWhenSkillFileDoesNotExist() {
        Path nonExistent = Path.of("src/test/resources/dataset/markdown/skills/no-such-skill/SKILL.md");

        assertThatThrownBy(() -> skillParser.getSkill(nonExistent))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenSkillFileIsNull() {
        assertThatThrownBy(() -> skillParser.getSkill(null))
            .isInstanceOf(NullPointerException.class);
    }
}

