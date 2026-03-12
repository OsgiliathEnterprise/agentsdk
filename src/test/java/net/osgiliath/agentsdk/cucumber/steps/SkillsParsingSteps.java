package net.osgiliath.agentsdk.cucumber.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillAsset;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.skills.parser.SkillScriptCommand;
import net.osgiliath.agentsdk.skills.parser.SkillTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Skills Parsing feature scenarios.
 */
public class SkillsParsingSteps {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private SkillParser skillParser;

    private Path skillFilePath;
    private Path skillDatasetRoot;
    private Skill skill;
    private Throwable stepError;

    private static final String EXPECTED_NAME = "implements features file";
    private static final String EXPECTED_DESCRIPTION = "You're a Gherkin scenario writer. You will be given a feature file and a user story and you will have to write the Gherkin scenarios to define acceptance criteria.";
    private static final List<String> EXPECTED_DEPENDENCIES = List.of("python>=3.8", "pandas>=1.5.0", "matplotlib");
    private static final List<String> EXPECTED_MCP = List.of("server-name-1", "server-name-2");
    private static final List<String> EXPECTED_LLM = List.of("claude-3-5-sonnet-20241022");
    private static final List<String> EXPECTED_ASSET_URIS = List.of("assets/eval_review.html");
    private static final List<String> EXPECTED_TEMPLATE_URIS = List.of("templates/generator_template.js");
    private static final List<SkillScriptCommand> EXPECTED_SCRIPT_COMMANDS =
        List.of(new SkillScriptCommand("./gradlew", "./gradlew scripts/build.gradle.kts ping"));

    @Before
    public void resetScenarioState() {
        skillFilePath = null;
        skillDatasetRoot = null;
        skill = null;
        stepError = null;
    }

    @Given("a skill file at {string}")
    public void aSkillFileAt(String relativePath) {
        safely(() -> {
            skillFilePath = resolveFromProject(relativePath);
            assertThat(skillFilePath).exists();
        });
    }

    @When("the skill parser reads the front matter header")
    public void theSkillParserReadsTheFrontMatterHeader() {
        safely(() -> skill = skillParser.getSkill(skillFilePath));
    }

    @Then("the parsed headers should contain:")
    public void theParsedHeadersShouldContain(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedKeys = firstColumnValues(dataTable, "key");
        assertThat(skill).isNotNull();
        assertThat(skill.headers().headerKeys()).containsAll(expectedKeys);
        assertThat(skill.headers().name().value()).isEqualTo(EXPECTED_NAME);
        assertThat(skill.headers().description().value()).isEqualTo(EXPECTED_DESCRIPTION);
        assertThat(skill.headers().dependencies().value()).containsExactlyElementsOf(EXPECTED_DEPENDENCIES);
        assertThat(skill.headers().mcp().value()).containsExactlyElementsOf(EXPECTED_MCP);
        assertThat(skill.headers().llm().value()).containsExactlyElementsOf(EXPECTED_LLM);
    }

    @Given("a parsed skill from {string}")
    public void aParsedSkillFrom(String relativePath) {
        safely(() -> {
            skillFilePath = resolveFromProject(relativePath);
            assertThat(skillFilePath).exists();
            skill = skillParser.getSkill(skillFilePath);
        });
    }

    @When("the parser resolves markdown links in the skill content")
    public void theParserResolvesMarkdownLinksInTheSkillContent() {
        safely(() -> assertThat(skill.content().sections()).isNotEmpty());
    }

    @Then("linked markdown files should be loaded and parsed")
    public void linkedMarkdownFilesShouldBeLoadedAndParsed() {
        assertNoSetupError();
        assertThat(skill.content().sections()).isNotEmpty();
        assertThat(skill.aggregateDocument()).contains("## Instructions");
        assertThat(skill.aggregateDocument()).contains("## Grader Agent");
    }

    @Then("the following linked markdown files should be followed:")
    public void theFollowingLinkedMarkdownFilesShouldBeFollowed(DataTable dataTable) {
        assertNoSetupError();
        String aggregate = skill.aggregateDocument();
        firstColumnValues(dataTable, "uri").forEach(uri -> assertThat(aggregate).contains(uri));
    }

    @Then("the uri reference {string} should be resolved in the content")
    public void theUriReferenceShouldBeResolvedInTheContent(String uriReference) {
        assertNoSetupError();
        assertThat(skill.aggregateDocument()).contains(uriReference);
    }

    @Then("^the uri reference \\[sample-skill\\]\\(examples/faq-answers\\.md\\) should be resolved in the content$")
    public void theUriReferenceMarkdownLinkShouldBeResolvedInTheContent() {
        theUriReferenceShouldBeResolvedInTheContent("examples/faq-answers.md");
    }

    @When("the parser resolves non-markdown links")
    public void theParserResolvesNonMarkdownLinks() {
        safely(() -> assertThat(skill.assets()).isNotNull());
    }

    @Then("asset URIs should be registered in the result")
    public void assetUrisShouldBeRegisteredInTheResult() {
        assertNoSetupError();
        assertThat(skill.assets()).isNotEmpty();
        assertThat(skill.assets()).map(SkillAsset::uri).containsExactlyElementsOf(EXPECTED_ASSET_URIS);
    }

    @Then("the following asset URI should be present:")
    public void theFollowingAssetUriShouldBePresent(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedUris = firstColumnValues(dataTable, "uri");
        List<String> actualUris = skill.assets().stream().map(SkillAsset::uri).toList();
        assertThat(actualUris).containsAll(expectedUris);
    }

    @Given("a skill dataset root at {string}")
    public void aSkillDatasetRootAt(String relativePath) {
        safely(() -> {
            skillDatasetRoot = resolveFromProject(relativePath);
            assertThat(skillDatasetRoot).isDirectory();
            skillFilePath = skillDatasetRoot.resolve("SKILL.md");
            assertThat(skillFilePath).exists();
        });
    }

    @When("the parser reads the {string} folder")
    public void theParserReadsTheFolder(String folderName) {
        safely(() -> {
            assertThat(folderName).isEqualTo("reference");
            skill = skillParser.getSkill(skillFilePath);
        });
    }

    @Then("reference markdown documents should be parsed")
    public void referenceMarkdownDocumentsShouldBeParsed() {
        assertNoSetupError();
        assertThat(skill.aggregateDocument()).contains("MCP Server Evaluation Guide");
        assertThat(skill.aggregateDocument()).contains("This document provides guidance on creating comprehensive evaluations for MCP servers.");
    }

    @Then("{string} should be present in parsed references")
    public void shouldBePresentInParsedReferences(String referenceUri) {
        assertNoSetupError();
        assertThat(referenceUri).contains("reference/");
        assertThat(skill.aggregateDocument()).contains("MCP Server Evaluation Guide");
    }

    @When("command blocks are extracted from skill instructions")
    public void commandBlocksAreExtractedFromSkillInstructions() {
        safely(() -> assertThat(skill.scriptCommands()).isNotNull());
    }

    @Then("script commands should be registered")
    public void scriptCommandsShouldBeRegistered() {
        assertNoSetupError();
        assertThat(skill.scriptCommands()).isNotEmpty();
        assertThat(skill.scriptCommands()).contains(new SkillScriptCommand("./gradlew", "./gradlew scripts/build.gradle.kts ping"));
        assertThat(skill.scriptCommands()).allMatch(cmd -> !cmd.executable().isBlank() && !cmd.commandLine().isBlank());
    }

    @Then("at least one command should start with:")
    public void atLeastOneCommandShouldStartWith(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedExecutables = firstColumnValues(dataTable, "executable");
        assertThat(skill.scriptCommands()).anyMatch(script ->
            expectedExecutables.stream().anyMatch(prefix -> script.executable().startsWith(prefix))
        );
        assertThat(skill.scriptCommands()).extracting(SkillScriptCommand::commandLine)
            .contains("./gradlew scripts/build.gradle.kts ping");
    }

    @When("template resources are scanned")
    public void templateResourcesAreScanned() {
        safely(() -> {
            if (skill == null) {
                skill = skillParser.getSkill(skillFilePath);
            }
        });
    }

    @Then("template URIs should be registered")
    public void templateUrisShouldBeRegistered() {
        assertNoSetupError();
        assertThat(skill.templates()).isNotEmpty();
        assertThat(skill.templates()).map(SkillTemplate::uri).containsExactlyElementsOf(EXPECTED_TEMPLATE_URIS);
    }

    @Then("{string} should be present in the template registry")
    public void shouldBePresentInTheTemplateRegistry(String templateUri) {
        assertNoSetupError();
        assertThat(skill.templates()).map(SkillTemplate::uri).anyMatch(uri -> uri.endsWith(templateUri));
    }

    @Given("a fully parsed skill with headers, body, links, assets, references, scripts, and templates")
    public void aFullyParsedSkillWithAllComponents() {
        safely(() -> {
            skillFilePath = resolveFromProject(
                "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md");
            skill = skillParser.getSkill(skillFilePath);
        });
    }

    @When("the parser composes the output model")
    public void theParserComposesTheOutputModel() {
        safely(() -> assertThat(skill).isNotNull());
    }

    @Then("the result should be split into distinct sections:")
    public void theResultShouldBeSplitIntoDistinctSections(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedSections = firstColumnValues(dataTable, "section");

        expectedSections.forEach(section -> {
            switch (section) {
                case "headers" -> {
                    assertThat(skill.headers()).isNotNull();
                    assertThat(skill.headers().name().value()).isEqualTo(EXPECTED_NAME);
                }
                case "assets" -> assertThat(skill.assets()).map(SkillAsset::uri)
                    .containsExactlyElementsOf(EXPECTED_ASSET_URIS);
                case "scripts" -> {
                    assertThat(skill.scriptCommands()).isNotEmpty();
                    assertThat(skill.scriptCommands()).contains(
                        new SkillScriptCommand("./gradlew", "./gradlew scripts/build.gradle.kts ping")
                    );
                }
                case "templates" -> assertThat(skill.templates()).map(SkillTemplate::uri)
                    .containsExactlyElementsOf(EXPECTED_TEMPLATE_URIS);
                case "content", "links", "references" -> assertThat(skill.content().sections()).isNotEmpty();
                default -> throw new IllegalArgumentException("Unknown expected section: " + section);
            }
        });
    }

    @Given("a fully parsed and composed skill result")
    public void aFullyParsedAndComposedSkillResult() {
        aFullyParsedSkillWithAllComponents();
    }

    @When("the aggregate document builder runs")
    public void theAggregateDocumentBuilderRuns() {
        safely(() -> assertThat(skill.aggregateDocument()).isNotNull());
    }

    @Then("a non-empty aggregate document should be created")
    public void aNonEmptyAggregateDocumentShouldBeCreated() {
        assertNoSetupError();
        assertThat(skill.aggregateDocument()).isNotBlank();
    }

    @Then("the aggregate document should include headers and composed content sections")
    public void theAggregateDocumentShouldIncludeHeadersAndComposedContentSections() {
        assertNoSetupError();
        assertThat(skill.aggregateDocument()).contains(skill.headers().name().value());
        assertThat(skill.aggregateDocument()).contains(skill.headers().description().value());
        assertThat(skill.aggregateDocument()).contains("## PPTX Skill");
        assertThat(skill.aggregateDocument()).contains("## Instructions");
        assertThat(skill.aggregateDocument()).contains("## MCP Server Evaluation Guide");
    }

    @Then("duplicate linked content should not be duplicated in the aggregate output")
    public void duplicateLinkedContentShouldNotBeDuplicatedInTheAggregateOutput() {
        assertNoSetupError();
        String aggregate = skill.aggregateDocument();
        String anchorPhrase = "You are an assistant for answering questions";
        assertThat(countOccurrences(aggregate, anchorPhrase)).isEqualTo(1);
    }

    private List<String> firstColumnValues(DataTable table, String headerName) {
        List<List<String>> rows = table.asLists();
        if (rows.isEmpty()) {
            return List.of();
        }
        int startIndex = rows.getFirst().size() == 1 && headerName.equalsIgnoreCase(rows.getFirst().getFirst()) ? 1 : 0;
        return rows.subList(startIndex, rows.size()).stream()
            .filter(row -> !row.isEmpty())
            .map(List::getFirst)
            .toList();
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private void assertNoSetupError() {
        assertThat(stepError).as("Given/When steps should complete without failures").isNull();
    }

    private void safely(ThrowingRunnable operation) {
        try {
            operation.run();
        } catch (Throwable throwable) {
            stepError = throwable;
        }
    }

    private Path resolveFromProject(String relativePath) {
        Path fromWorkDir = Path.of(relativePath);
        if (Files.exists(fromWorkDir)) {
            return fromWorkDir.toAbsolutePath().normalize();
        }
        try {
            var resource = getClass().getClassLoader().getResource(".");
            if (resource != null) {
                Path classesDir = Path.of(resource.toURI());
                Path moduleRoot = classesDir.getParent().getParent().getParent().getParent();
                Path candidate = moduleRoot.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
                Path projectRoot = moduleRoot.getParent();
                candidate = projectRoot.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        } catch (URISyntaxException e) {
            // fall through
        }
        return fromWorkDir.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
