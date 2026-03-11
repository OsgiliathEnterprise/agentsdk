package net.osgiliath.agentsdk.cucumber.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Skills Parsing feature scenarios.
 */
public class SkillsParsingSteps {

    // ---- shared scenario state ----
    private Path skillFilePath;
    private Path skillDatasetRoot;
    private Object parsedSkill;            // placeholder – replace with real type
    private Map<String, Object> parsedHeaders;
    private List<String> resolvedMarkdownLinks;
    private List<String> resolvedAssetUris;
    private List<String> parsedReferences;
    private List<String> registeredScripts;
    private List<String> registeredTemplates;
    private Object composedResult;         // placeholder – replace with real type
    private Object aggregateDocument;      // placeholder – replace with real type
    private Throwable stepError;

    @Before
    public void resetScenarioState() {
        skillFilePath = null;
        skillDatasetRoot = null;
        parsedSkill = null;
        parsedHeaders = null;
        resolvedMarkdownLinks = new ArrayList<>();
        resolvedAssetUris = new ArrayList<>();
        parsedReferences = new ArrayList<>();
        registeredScripts = new ArrayList<>();
        registeredTemplates = new ArrayList<>();
        composedResult = null;
        aggregateDocument = null;
        stepError = null;
    }

    // ========================
    // SC1 – skill headers should be parsed
    // ========================

    @Given("a skill file at {string}")
    public void aSkillFileAt(String relativePath) {
        safely(() -> {
            skillFilePath = resolveFromProject(relativePath);
            assertThat(skillFilePath).exists();
        });
    }

    @When("the skill parser reads the front matter header")
    public void theSkillParserReadsTheFrontMatterHeader() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("the parsed headers should contain:")
    public void theParsedHeadersShouldContain(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedKeys = dataTable.asList();
        assertThat(parsedHeaders).isNotNull();
        assertThat(parsedHeaders.keySet()).containsAll(expectedKeys);
    }

    // ========================
    // SC2 – markdown links should be followed and parsed
    // ========================

    @Given("a parsed skill from {string}")
    public void aParsedSkillFrom(String relativePath) {
        safely(() -> {
            skillFilePath = resolveFromProject(relativePath);
            assertThat(skillFilePath).exists();
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @When("the parser resolves markdown links in the skill content")
    public void theParserResolvesMarkdownLinksInTheSkillContent() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("linked markdown files should be loaded and parsed")
    public void linkedMarkdownFilesShouldBeLoadedAndParsed() {
        assertNoSetupError();
        assertThat(resolvedMarkdownLinks).isNotEmpty();
    }

    @Then("the following linked markdown files should be followed:")
    public void theFollowingLinkedMarkdownFilesShouldBeFollowed(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedUris = dataTable.asList();
        assertThat(resolvedMarkdownLinks).containsAll(expectedUris);
    }

    @Then("the uri reference {string} should be resolved in the content")
    public void theUriReferenceShouldBeResolvedInTheContent(String uriReference) {
        assertNoSetupError();
        assertThat(resolvedMarkdownLinks).anyMatch(link -> link.contains(uriReference)
            || uriReference.contains(link));
    }

    // ========================
    // SC3 – assets uris should be stored
    // ========================

    @When("the parser resolves non-markdown links")
    public void theParserResolvesNonMarkdownLinks() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("asset URIs should be registered in the result")
    public void assetUrisShouldBeRegisteredInTheResult() {
        assertNoSetupError();
        assertThat(resolvedAssetUris).isNotEmpty();
    }

    @Then("the following asset URI should be present:")
    public void theFollowingAssetUriShouldBePresent(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedUris = dataTable.asList();
        assertThat(resolvedAssetUris).containsAll(expectedUris);
    }

    // ========================
    // SC4 – reference should be parsed
    // ========================

    @Given("a skill dataset root at {string}")
    public void aSkillDatasetRootAt(String relativePath) {
        safely(() -> {
            skillDatasetRoot = resolveFromProject(relativePath);
            assertThat(skillDatasetRoot).isDirectory();
        });
    }

    @When("the parser reads the {string} folder")
    public void theParserReadsTheFolder(String folderName) {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("reference markdown documents should be parsed")
    public void referenceMarkdownDocumentsShouldBeParsed() {
        assertNoSetupError();
        assertThat(parsedReferences).isNotEmpty();
    }

    @Then("{string} should be present in parsed references")
    public void shouldBePresentInParsedReferences(String referenceUri) {
        assertNoSetupError();
        assertThat(parsedReferences).anyMatch(ref -> ref.endsWith(referenceUri) || ref.contains(referenceUri));
    }

    // ========================
    // SC5 – scripts commands should be registered from skill instruction
    // ========================

    @When("command blocks are extracted from skill instructions")
    public void commandBlocksAreExtractedFromSkillInstructions() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("script commands should be registered")
    public void scriptCommandsShouldBeRegistered() {
        assertNoSetupError();
        assertThat(registeredScripts).isNotEmpty();
    }

    @Then("at least one command should start with:")
    public void atLeastOneCommandShouldStartWith(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedExecutables = dataTable.asList();
        assertThat(registeredScripts).anyMatch(script ->
            expectedExecutables.stream().anyMatch(script::startsWith)
        );
    }

    // ========================
    // SC6 – templates uris should be registered
    // ========================

    @When("template resources are scanned")
    public void templateResourcesAreScanned() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("template URIs should be registered")
    public void templateUrisShouldBeRegistered() {
        assertNoSetupError();
        assertThat(registeredTemplates).isNotEmpty();
    }

    @Then("{string} should be present in the template registry")
    public void shouldBePresentInTheTemplateRegistry(String templateUri) {
        assertNoSetupError();
        assertThat(registeredTemplates).anyMatch(t -> t.endsWith(templateUri) || t.contains(templateUri));
    }

    // ========================
    // SC7 – results should be composed in different sections
    // ========================

    @Given("a fully parsed skill with headers, body, links, assets, references, scripts, and templates")
    public void aFullyParsedSkillWithAllComponents() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @When("the parser composes the output model")
    public void theParserComposesTheOutputModel() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("the result should be split into distinct sections:")
    public void theResultShouldBeSplitIntoDistinctSections(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedSections = dataTable.asList();
        assertThat(composedResult).isNotNull();
        // TODO: assert each section exists in composedResult once the type is defined
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ========================
    // SC8 – aggregate document should be created
    // ========================

    @Given("a fully parsed and composed skill result")
    public void aFullyParsedAndComposedSkillResult() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @When("the aggregate document builder runs")
    public void theAggregateDocumentBuilderRuns() {
        safely(() -> {
            throw new UnsupportedOperationException("Not implemented yet");
        });
    }

    @Then("a non-empty aggregate document should be created")
    public void aNonEmptyAggregateDocumentShouldBeCreated() {
        assertNoSetupError();
        assertThat(aggregateDocument).isNotNull();
    }

    @Then("the aggregate document should include headers and composed content sections")
    public void theAggregateDocumentShouldIncludeHeadersAndComposedContentSections() {
        assertNoSetupError();
        assertThat(aggregateDocument).isNotNull();
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("duplicate linked content should not be duplicated in the aggregate output")
    public void duplicateLinkedContentShouldNotBeDuplicatedInTheAggregateOutput() {
        assertNoSetupError();
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ========================
    // Helpers
    // ========================

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

    /**
     * Resolves a project-relative path (relative to the repository root) from the classpath.
     * Falls back to resolving from the working directory.
     */
    private Path resolveFromProject(String relativePath) {
        // Try from working directory (running from project root)
        Path fromWorkDir = Path.of(relativePath);
        if (Files.exists(fromWorkDir)) {
            return fromWorkDir.toAbsolutePath().normalize();
        }
        // Try climbing up from the classloader resource root
        try {
            var resource = getClass().getClassLoader().getResource(".");
            if (resource != null) {
                Path classesDir = Path.of(resource.toURI());
                // Navigate up to project root (classes/java/test -> build -> module root)
                Path moduleRoot = classesDir.getParent().getParent().getParent().getParent();
                Path candidate = moduleRoot.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
                // Try one level up (multi-module project root)
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

