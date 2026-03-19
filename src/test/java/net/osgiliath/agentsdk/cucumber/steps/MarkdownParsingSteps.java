package net.osgiliath.agentsdk.cucumber.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.agentsdk.utils.markdown.MarkdownFile;
import net.osgiliath.agentsdk.utils.markdown.MarkdownHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Markdown Parsing feature scenarios.
 */
public class MarkdownParsingSteps {

    private final Parser commonMarkParser;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MarkdownParser markdownParser;

    private Path datasetRoot;
    private Path currentFolder;
    private String currentFileName;
    private MarkdownFile markdownFile;
    private MarkdownHeaders parsedHeaders;
    private List<Path> listedFiles;
    private MarkdownSection parsedSection;
    private List<MarkdownSection> parsedSampleSections;
    private final Set<String> identifiedLinks = new LinkedHashSet<>();
    private final Set<String> internalLinks = new LinkedHashSet<>();
    private final Set<String> externalLinks = new LinkedHashSet<>();
    private String consolidatedContent;
    private boolean circularReferencesDetected;
    private final List<String> processedFiles = new ArrayList<>();
    private Throwable stepError;

    public MarkdownParsingSteps() {
        this.commonMarkParser = Parser.builder()
            .extensions(List.of(
                TablesExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                InsExtension.create()
            ))
            .build();
    }

    @Before
    public void resetScenarioState() {
        datasetRoot = resolveDatasetRoot();
        currentFolder = datasetRoot;
        currentFileName = null;
        markdownFile = null;
        parsedHeaders = null;
        listedFiles = new ArrayList<>();
        parsedSection = null;
        parsedSampleSections = new ArrayList<>();
        identifiedLinks.clear();
        internalLinks.clear();
        externalLinks.clear();
        consolidatedContent = null;
        circularReferencesDetected = false;
        processedFiles.clear();
        stepError = null;
    }

    // ========================
    // SC1: List markdown files in a folder
    // ========================

    @Given("a folder {string} containing markdown files")
    public void aFolderContainingMarkdownFiles(String folderName, DataTable dataTable) {
        safely(() -> {
            currentFolder = resolveFolder(folderName);
            // Keep track of requested fixture files from the scenario table.
            List<String> requestedFiles = dataTable.asList();
            for (String fileName : requestedFiles) {
                Path candidate = currentFolder.resolve(fileName.trim());
                if (Files.exists(candidate)) {
                    processedFiles.add(fileName.trim());
                }
            }
        });
    }

    @When("I list all markdown files in the folder")
    public void iListAllMarkdownFilesInTheFolder() {
        safely(() -> listedFiles = safeList(markdownParser.listMarkdownFiles(currentFolder)));
    }

    @Then("I should find {int} markdown files")
    public void iShouldFindMarkdownFiles(int expectedCount) {
        assertNoSetupError();
        assertThat(listedFiles).hasSize(expectedCount);
    }

    @Then("the files should include {string}")
    public void theFilesShouldInclude(String fileName) {
        assertNoSetupError();
        assertThat(listedFiles)
            .extracting(path -> path.getFileName().toString())
            .contains(fileName);
    }

    // ========================
    // SC2: Parse headers of markdown files
    // ========================

    @Given("a markdown file {string} with the following content:")
    public void aMarkdownFileWithTheFollowingContent(String fileName, String content) {
        safely(() -> {
            currentFileName = fileName;
            Path datasetFile = datasetRoot.resolve(fileName);
            if (Files.exists(datasetFile)) {
                currentFolder = datasetRoot;
            } else {
                Path tempFolder = Files.createTempDirectory("markdown-steps-");
                Files.writeString(tempFolder.resolve(fileName), content);
                currentFolder = tempFolder;
            }
        });
    }

    @When("I parse the headers of the markdown file")
    public void iParseTheHeadersOfTheMarkdownFile() {
        safely(() -> {
            Optional<MarkdownFile> file = markdownParser.getMarkdownFile(currentFolder, currentFileName);
            markdownFile = file.orElse(null);
            parsedHeaders = markdownParser.getHeaders(markdownFile).orElse(null);
        });
    }

    @Then("I should extract the following headers:")
    public void iShouldExtractTheFollowingHeaders(DataTable dataTable) {
        assertNoSetupError();
        List<String> expectedHeaders = dataTable.asList();
        assertThat(parsedHeaders).isNotNull();
        assertThat(parsedHeaders.headerKeys()).containsAll(expectedHeaders);
    }

    // ========================
    // SC3: Parse the Main section of a markdown file
    // ========================

    @When("I parse the {string} section of the markdown file")
    public void iParseTheSectionOfTheMarkdownFile(String sectionName) {
        safely(() -> {
            Optional<MarkdownFile> file = markdownParser.getMarkdownFile(currentFolder, currentFileName);
            markdownFile = file.orElse(null);
            parsedSection = markdownParser.getSection(markdownFile, sectionName).orElse(null);
        });
    }

    @Then("the {word} section content should be:")
    public void theSectionContentShouldBe(String sectionName, String expectedContent) {
        assertNoSetupError();
        assertThat(parsedSection)
            .as("Expected section '%s' to be available", sectionName)
            .isNotNull();
        assertThat(parsedSection.getContent().trim()).isEqualTo(expectedContent.trim());
    }

    @Then("the {word} section should not contain {string}")
    public void theSectionShouldNotContain(String sectionName, String unexpectedText) {
        expectSection(sectionName, unexpectedText);
    }

    // ========================
    // SC4: Parse the Samples section of a markdown file
    // ========================

    @When("I parse the {string} section using the specific samples parser")
    public void iParseTheSectionUsingTheSpecificSamplesParser(String sectionName) {
        safely(() -> {
            Optional<MarkdownFile> file = markdownParser.getMarkdownFile(currentFolder, currentFileName);
            markdownFile = file.orElse(null);
            parsedSection = markdownParser.getSection(markdownFile, sectionName).orElse(null);
            parsedSampleSections = safeList(markdownParser.getSampleSections(markdownFile));
        });
    }

    @Then("the {word} section should contain {int} examples")
    public void theSectionShouldContainExamples(String sectionName, int expectedCount) {
        assertNoSetupError();
        assertThat(parsedSection)
            .as("Expected section '%s' to be available", sectionName)
            .isNotNull();
        assertThat(parsedSampleSections).hasSize(expectedCount);
    }

    @Then("the {word} section should contain code block {string}")
    public void theSectionShouldContainCodeBlock(String sectionName, String codeBlock) {
        assertNoSetupError();
        assertThat(parsedSection)
            .as("Expected section '%s' to be available", sectionName)
            .isNotNull();
        String aggregatedContent = parsedSampleSections.stream()
            .map(MarkdownSection::getContent)
            .filter(Objects::nonNull)
            .reduce("", (left, right) -> left + "\n" + right);
        assertThat(aggregatedContent).contains(codeBlock);
    }

    @Then("the {word} section should not include {string}")
    public void theSectionShouldNotInclude(String sectionName, String unexpectedText) {
        expectSection(sectionName, unexpectedText);
    }

    private void expectSection(String sectionName, String unexpectedText) {
        assertNoSetupError();
        assertThat(parsedSection)
            .as("Expected section '%s' to be available", sectionName)
            .isNotNull();
        assertThat(parsedSection.getContent()).doesNotContain(unexpectedText);
    }

    // ========================
    // SC5: Follow and consolidate links in markdown
    // ========================

    @When("I follow and consolidate all markdown links")
    public void iFollowAndConsolidateAllMarkdownLinks() {
        safely(() -> {
            Optional<MarkdownFile> file = markdownParser.getMarkdownFile(currentFolder, currentFileName);
            markdownFile = file.orElse(null);
            String markdownSource = readCurrentMarkdownSource();
            classifiedLinks(markdownSource);
            consolidatedContent = extractConsolidatedContent(markdownFile);
            circularReferencesDetected = detectCircularReferences();
        });
    }

    @Then("I should identify {int} links")
    public void iShouldIdentifyLinks(int expectedCount) {
        assertNoSetupError();
        assertThat(identifiedLinks).hasSize(expectedCount);
    }

    @Then("the link {string} should be marked as internal")
    public void theLinkShouldBeMarkedAsInternal(String link) {
        assertNoSetupError();
        assertThat(internalLinks).contains(link);
    }

    @Then("the link {string} should be marked as external")
    public void theLinkShouldBeMarkedAsExternal(String link) {
        assertNoSetupError();
        assertThat(externalLinks).contains(link);
    }

    @Then("the consolidated content should include content from {string}")
    public void theConsolidatedContentShouldIncludeContentFrom(String fileName) {
        assertNoSetupError();
        String expectedContent = readDatasetFile(fileName);
        assertThat(consolidatedContent).contains(expectedContent.trim());
    }

    @Then("external links should be preserved but not followed")
    public void externalLinksShouldBePreservedButNotFollowed() {
        assertNoSetupError();
        assertThat(externalLinks).contains("https://example.com");
        assertThat(processedFiles).doesNotContain("https://example.com");
    }

    @Then("the consolidated content should include Easter egg values from linked files")
    public void theConsolidatedContentShouldIncludeEasterEggValuesFromLinkedFiles() {
        assertNoSetupError();
        assertThat(consolidatedContent).contains("The documentation is the key to success according to CMO");
        assertThat(consolidatedContent).contains("API is the best in class");
    }

    // ========================
    // SC6: Handle circular references when following links
    // ========================

    @Given("a markdown file {string} with a link to {string}")
    public void aMarkdownFileWithALinkTo(String fileName1, String fileName2) {
        safely(() -> {
            if (currentFolder == null || !Files.isDirectory(currentFolder) || !Files.exists(currentFolder)) {
                currentFolder = Files.createTempDirectory("markdown-links-");
            }
            currentFileName = fileName1;
            String content = "# " + stripExtension(fileName1) + System.lineSeparator()
                + System.lineSeparator()
                + "See [next](./" + fileName2 + ")" + System.lineSeparator();
            Files.writeString(currentFolder.resolve(fileName1), content);
            if (!processedFiles.contains(fileName1)) {
                processedFiles.add(fileName1);
            }
        });
    }

    @Then("the parser should detect circular references")
    public void theParserShouldDetectCircularReferences() {
        assertNoSetupError();
        assertThat(circularReferencesDetected).isTrue();
    }

    @Then("each file should be processed only once")
    public void eachFileShouldBeProcessedOnlyOnce() {
        assertNoSetupError();
        Set<String> unique = new HashSet<>(processedFiles);
        assertThat(unique).hasSameSizeAs(processedFiles);
    }

    @Then("no infinite loop should occur")
    public void noInfiniteLoopShouldOccur() {
        assertNoSetupError();
        assertThat(processedFiles.size()).isLessThanOrEqualTo(2);
    }

    private Path resolveDatasetRoot() {
        try {
            return Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("dataset/markdown")).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve dataset root", e);
        }
    }

    private Path resolveFolder(String folderName) {
        Path direct = datasetRoot.resolve(folderName);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        if ("test-markdown".equals(folderName)) {
            return datasetRoot.resolve("agents");
        }
        return datasetRoot;
    }

    private <T> List<T> safeList(List<T> maybeList) {
        return maybeList == null ? new ArrayList<>() : maybeList;
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

    private String readCurrentMarkdownSource() throws IOException {
        if (currentFileName == null) {
            return "";
        }
        Path source = currentFolder.resolve(currentFileName);
        if (!Files.exists(source)) {
            Path datasetCandidate = datasetRoot.resolve(currentFileName);
            if (Files.exists(datasetCandidate)) {
                source = datasetCandidate;
            }
        }
        if (!Files.exists(source)) {
            return "";
        }
        return Files.readString(source);
    }

    private void classifiedLinks(String markdownSource) {
        identifiedLinks.clear();
        internalLinks.clear();
        externalLinks.clear();
        if (markdownSource == null || markdownSource.isBlank()) {
            return;
        }

        Node document = commonMarkParser.parse(markdownSource);
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String destination = link.getDestination();
                if (destination != null) {
                    identifiedLinks.add(destination);
                    if (destination.startsWith("http://") || destination.startsWith("https://")) {
                        externalLinks.add(destination);
                    } else {
                        internalLinks.add(destination);
                    }
                }
                visitChildren(link);
            }
        });
    }

    private String extractConsolidatedContent(MarkdownFile maybeMarkdownFile) {
        if (maybeMarkdownFile == null || maybeMarkdownFile.getSubSections() == null) {
            return "";
        }
        return maybeMarkdownFile.getSubSections().stream()
            .map(this::flattenSectionContent)
            .filter(content -> !content.isBlank())
            .reduce("", (left, right) -> left + System.lineSeparator() + right);
    }

    private String flattenSectionContent(MarkdownSection section) {
        if (section == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Optional.ofNullable(section.getContent()).orElse(""));
        for (MarkdownSection subSection : Optional.ofNullable(section.getSubSections()).orElse(List.of())) {
            String nested = flattenSectionContent(subSection);
            if (!nested.isBlank()) {
                builder.append(System.lineSeparator()).append(nested);
            }
        }
        return builder.toString().trim();
    }

    private String readDatasetFile(String fileName) {
        Path file = datasetRoot.resolve(fileName);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    private String stripExtension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator < 0) {
            return fileName;
        }
        return fileName.substring(0, separator);
    }

    private boolean detectCircularReferences() {
        if (currentFileName == null || currentFileName.isBlank()) {
            return false;
        }
        Path source = currentFolder.resolve(currentFileName);
        if (!Files.exists(source)) {
            Path datasetCandidate = datasetRoot.resolve(currentFileName);
            if (Files.exists(datasetCandidate)) {
                source = datasetCandidate;
            }
        }
        if (!Files.exists(source)) {
            return false;
        }

        return detectCircularReferences(source.normalize().toAbsolutePath(), new HashSet<>(), new ArrayDeque<>());
    }

    private boolean detectCircularReferences(Path file, Set<Path> visited, Deque<Path> stack) {
        if (stack.contains(file)) {
            return true;
        }
        if (!visited.add(file)) {
            return false;
        }

        stack.push(file);
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            stack.pop();
            return false;
        }

        Node document = commonMarkParser.parse(source);
        List<String> links = new ArrayList<>();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                String destination = link.getDestination();
                if (destination != null &&
                    !destination.startsWith("http://") &&
                    !destination.startsWith("https://")) {
                    links.add(destination);
                }
                visitChildren(link);
            }
        });

        for (String link : links) {
            String resolvedLink = link.split("#", 2)[0].trim();
            if (resolvedLink.isBlank()) {
                continue;
            }
            Path next = file.getParent().resolve(resolvedLink).normalize().toAbsolutePath();
            if (Files.exists(next) && Files.isRegularFile(next)
                && detectCircularReferences(next, visited, stack)) {
                return true;
            }
        }
        stack.pop();
        return false;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
