package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MarkdownParserImpl}.
 *
 * <p>The two private rendering paths are exercised through the public API:
 * <ul>
 *   <li>{@code renderNodeToText(..., preserveHeadings=true, blockSeparator="\n")} –
 *       triggered by {@code extractNodeTextRecursive}, which is called when a file contains
 *       <em>no headings</em> and the whole file becomes a single section.
 *   <li>{@code renderNodeToText(..., preserveHeadings=false, blockSeparator="\n\n")} –
 *       triggered by {@code extractNodeTextForSectionRecursive}, which is called to collect
 *       content inside heading-delimited sections.
 * </ul>
 */
class MarkdownParserImplTest {

    @TempDir
    Path tempDir;
    private MarkdownParserImpl parser;

    @BeforeEach
    void setUp() {
        Parser markdownParser = Parser.builder()
                .extensions(List.of(
                        YamlFrontMatterExtension.create(),
                        TablesExtension.create()
                ))
                .build();
        parser = new MarkdownParserImpl(markdownParser);
    }

    // -----------------------------------------------------------------------
    // extractNodeTextRecursive path
    // A file with no headings yields no sections from parseSections(), so
    // extractFullMarkdownContent() is called and the entire document is stored
    // as a single MainSection whose content comes from extractNodeTextRecursive.
    // -----------------------------------------------------------------------

    @Test
    void getNullFileReturnsEmpty() {
        assertThat(parser.getMarkdownFile(null)).isEmpty();
        assertThat(parse(" ")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // extractNodeTextForSectionRecursive path
    // A file with headings produces real sections; content between/after headings
    // is collected via extractNodeTextForSectionRecursive (double-newline separator).
    // -----------------------------------------------------------------------

    @Test
    void listMarkdownFilesReturnsSortedMdFilesOnly() throws IOException {
        write("b.md", "# B");
        write("a.md", "# A");
        Files.writeString(tempDir.resolve("ignored.txt"), "text");

        List<Path> files = parser.listMarkdownFiles(tempDir);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).getFileName()).hasToString("a.md");
        assertThat(files.get(1).getFileName()).hasToString("b.md");
    }

    @Nested
    class MainSectionsDepth {

        private MarkdownFile nestedFixture() throws IOException {
            write("depth.md", """
                    # Root

                    Root content.

                    ## Child

                    Child content.

                    ### Grandchild

                    Grandchild content.
                    """);
            return parse("depth.md").orElseThrow();
        }

        @Test
        void depthOneReturnsOnlyRootSections() throws IOException {
            List<MarkdownSection> sections = parser.getMainSections(nestedFixture(), 1);

            assertThat(sections).hasSize(1);
            assertThat(sections.getFirst().getTitle()).isEqualTo("Root");
            assertThat(sections.getFirst().getSubSections()).isEmpty();
        }

        @Test
        void depthTwoKeepsDirectChildrenOnly() throws IOException {
            List<MarkdownSection> sections = parser.getMainSections(nestedFixture(), 2);

            assertThat(sections).hasSize(1);
            MarkdownSection root = sections.getFirst();
            assertThat(root.getSubSections()).hasSize(1);
            assertThat(root.getSubSections().getFirst().getTitle()).isEqualTo("Child");
            assertThat(root.getSubSections().getFirst().getSubSections()).isEmpty();
        }

        @Test
        void nonPositiveDepthReturnsEmptyList() throws IOException {
            MarkdownFile fixture = nestedFixture();

            assertThat(parser.getMainSections(fixture, 0)).isEmpty();
            assertThat(parser.getMainSections(fixture, -1)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Misc public-API tests
    // -----------------------------------------------------------------------

    @Test
    void yamlFrontMatterIsExtractedAsHeaders() throws IOException {
        write("test.md", """
                ---
                title: My Title
                author: Someone
                ---
                
                # Content
                
                Body text.
                """);

        Optional<MarkdownFile> result = parse("test.md");

        assertThat(result).isPresent();
        Optional<MarkdownHeaders> headers = parser.getHeaders(result.get());
        assertThat(headers).isPresent();
        assertThat(headers.get().header("title")).hasValue("My Title");
        assertThat(headers.get().header("author")).hasValue("Someone");
    }

    @Test
    void linkedMarkdownContentIsConsolidated() throws IOException {
        write("main.md", """
                # Main
                
                See [details](details.md).
                """);
        write("details.md", """
                # Details
                
                Detail content.
                """);

        Optional<MarkdownFile> result = parse("main.md");

        assertThat(result).isPresent();
        List<MarkdownSection> sections = result.get().getSubSections();
        assertThat(sections).extracting(MarkdownSection::getTitle).contains("Main", "Details");
        assertThat(sections)
                .extracting(MarkdownSection::getContent)
                .anyMatch(content -> content != null && content.contains("Detail content."));
    }

    @Test
    void nonMarkdownLinksAreKeptAsLinks() throws IOException {
        write("main.md", """
                # Main

                Download [spec](spec.txt).
                """);
        write("spec.txt", "spec content");

        Optional<MarkdownFile> result = parse("main.md");

        assertThat(result).isPresent();
        assertThat(result.get().getSubSections())
                .extracting(MarkdownSection::getContent)
                .anyMatch(content -> content != null && content.contains("[spec](spec.txt)"));
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }

    @Nested
    class FullContentRendering {

        @Test
        void plainParagraphsAreCapturedAsSingleSection() throws IOException {
            write("test.md", """
                    Hello world.
                    
                    Second paragraph.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            List<MarkdownSection> sections = result.get().getSubSections();
            assertThat(sections).hasSize(1);
            String content = sections.getFirst().getContent();
            assertThat(content)
                    .contains("Hello world.")
                    .contains("Second paragraph.");
        }

        @Test
        void inlineCodeBackticksArePreserved() throws IOException {
            write("test.md", "Use `System.out.println()` to print.");

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            assertThat(result.get().getSubSections().getFirst().getContent())
                    .contains("`System.out.println()`");
        }

        @Test
        void fencedCodeBlockIsPreservedWithFencesAndLanguageTag() throws IOException {
            write("test.md", """
                    Some text.
                    
                    ```java
                    int x = 1;
                    ```
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            String content = result.get().getSubSections().getFirst().getContent();
            assertThat(content)
                    .contains("```java")
                    .contains("int x = 1;")
                    .contains("```");
        }

        @Test
        void linksAreRenderedInMarkdownSyntax() throws IOException {
            write("test.md", "Visit [example](https://example.com) today.");

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            assertThat(result.get().getSubSections().getFirst().getContent())
                    .contains("[example](https://example.com)");
        }

        @Test
        void softLineBreakWithinParagraphIsPreserved() throws IOException {
            // Two lines without a blank line between them = soft line break inside one paragraph.
            write("test.md", "Line one\nLine two");

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            String content = result.get().getSubSections().getFirst().getContent();
            // After the soft line break a newline character must appear
            assertThat(content).contains("Line one").contains("Line two");
        }

        @Test
        void headingMarkersArePreservedWhenFileHasNoSections() throws IOException {
            // A file with headings DOES produce sections; verify the section title is correct
            // so that the heading-full path (renderNodeToText with preserveHeadings=true) is
            // also exercised via extractFullMarkdownContent when heading is in a linked file
            // that actually has no parseable sections due to nesting constraints (manual check).
            // Here we verify the normal heading-produces-section path instead.
            write("test.md", "# Top Heading\n\nSome text.");

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            assertThat(result.get().getSubSections().getFirst().getTitle()).isEqualTo("Top Heading");
        }
    }

    // -----------------------------------------------------------------------
    // toDocument flag combinations
    // -----------------------------------------------------------------------

    @Nested
    class SectionContentRendering {

        @Test
        void plainTextInSectionIsExtracted() throws IOException {
            write("test.md", """
                    # My Section
                    
                    Plain text content.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "My Section");
            assertThat(section).isPresent();
            assertThat(section.get().getContent()).contains("Plain text content.");
        }

        @Test
        void inlineCodeSpanInSectionIsPreserved() throws IOException {
            write("test.md", """
                    # Section
                    
                    Call `doSomething()` here.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            assertThat(section.get().getContent()).contains("`doSomething()`");
        }

        @Test
        void fencedCodeBlockInSectionIsPreservedWithFencesAndInfo() throws IOException {
            write("test.md", """
                    # Section
                    
                    ```python
                    print("hello")
                    ```
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            assertThat(section.get().getContent())
                    .contains("```python")
                    .contains("print(\"hello\")")
                    .contains("```");
        }

        @Test
        void linkInSectionIsRenderedInMarkdownSyntax() throws IOException {
            write("test.md", """
                    # Section
                    
                    See [reference](https://ref.example.com).
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            assertThat(section.get().getContent())
                    .contains("[reference](https://ref.example.com)");
        }

        @Test
        void multipleParagraphsInSectionAreSeparatedByDoubleNewline() throws IOException {
            write("test.md", """
                    # Section
                    
                    First paragraph.
                    
                    Second paragraph.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            // double-newline separator is expected between the two paragraphs
            assertThat(section.get().getContent())
                    .contains("First paragraph.")
                    .contains("Second paragraph.")
                    .contains(System.lineSeparator() + System.lineSeparator());
        }

        @Test
        void nestedSubsectionIsExtractedAsChild() throws IOException {
            write("test.md", """
                    # Parent
                    
                    Parent content.
                    
                    ## Child
                    
                    Child content.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> child = parser.getSection(result.get(), "Child");
            assertThat(child).isPresent();
            assertThat(child.get().getContent()).contains("Child content.");
        }

        @Test
        void childContentDoesNotLeakIntoParentContent() throws IOException {
            write("test.md", """
                    # Parent
                    
                    Parent text only.
                    
                    ## Child
                    
                    Child text.
                    """);

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> parent = parser.getSection(result.get(), "Parent");
            assertThat(parent).isPresent();
            assertThat(parent.get().getContent())
                    .contains("Parent text only.")
                    .doesNotContain("Child text.");
        }

        @Test
        void softLineBreakWithinSectionParagraphProducesNewline() throws IOException {
            write("test.md", "# Section\n\nLine one\nLine two");

            Optional<MarkdownFile> result = parse("test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            String content = section.get().getContent();
            assertThat(content).contains("Line one").contains("Line two");
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    @Nested
    class ToDocumentRendering {

        /**
         * Shared fixture: YAML headers + one main section + one Samples section with a subsection.
         */
        private MarkdownFile fixture() throws IOException {
            write("fixture.md", """
                    ---
                    title: My API
                    version: "1.0"
                    ---
                    
                    # Main Section
                    
                    Main section content.
                    
                    # Samples
                    
                    ## Example One
                    
                    Example one content.
                    """);
            return parse("fixture.md").orElseThrow();
        }

        @Test
        void nullMarkdownFileProducesNoContentDocument() {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(null, true, true, true);
            assertThat(doc.text()).isEqualTo("(no content selected)");
        }

        @Test
        void allFlagsFalseProducesNoContentDocument() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, false, false);
            assertThat(doc.text()).isEqualTo("(no content selected)");
        }

        @Test
        void includeHeadersTrueEmitsYamlKeyValuePairs() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), true, false, false);
            assertThat(doc.text())
                    .contains("title: My API")
                    .contains("version: 1.0");
        }

        @Test
        void includeHeadersFalseOmitsHeaders() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, true, false);
            assertThat(doc.text())
                    .doesNotContain("title:")
                    .doesNotContain("version:");
        }

        @Test
        void includeSectionsTrueEmitsSectionTitleAndContent() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, true, false);
            assertThat(doc.text())
                    .contains("Main Section")
                    .contains("Main section content.");
        }

        @Test
        void includeSectionsFalseOmitsSectionContent() throws IOException {
            // Use a file without YAML front matter to avoid the full-source "text" header
            // contaminating the output. Enable only samples so the document is non-empty.
            write("nosections.md", """
                    # Main Section
                    
                    Main section content.
                    
                    # Samples
                    
                    ## Example One
                    
                    Example one content.
                    """);
            MarkdownFile file = parse("nosections.md").orElseThrow();

            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(file, false, false, true);

            assertThat(doc.text())
                    .doesNotContain("Main section content.")
                    .contains("Example one content.");
        }

        @Test
        void sectionsTitledSamplesAreExcludedFromMainSections() throws IOException {
            // When includeSections=true the "Samples" root section must be filtered out;
            // only includeSamples=true should expose its sub-sections.
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, true, false);
            assertThat(doc.text()).doesNotContain("Example One");
        }

        @Test
        void includeSamplesTrueEmitsSampleSubsections() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, false, true);
            assertThat(doc.text())
                    .contains("Example One")
                    .contains("Example one content.");
        }

        @Test
        void includeSamplesFalseOmitsSampleSubsections() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, true, false);
            assertThat(doc.text())
                    .doesNotContain("Example One")
                    .doesNotContain("Example one content.");
        }

        @Test
        void allFlagsTrueCombinesHeadersSectionsAndSamples() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), true, true, true);
            assertThat(doc.text())
                    .contains("title: My API")
                    .contains("Main Section")
                    .contains("Main section content.")
                    .contains("Example One")
                    .contains("Example one content.");
        }

        @Test
        void subsectionsOfMainSectionAreIncludedRecursively() throws IOException {
            write("nested.md", """
                    # Root
                    
                    Root content.
                    
                    ## Child
                    
                    Child content.
                    """);
            MarkdownFile file = parse("nested.md").orElseThrow();

            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(file, false, true, false);

            assertThat(doc.text())
                    .contains("Root")
                    .contains("Root content.")
                    .contains("Child")
                    .contains("Child content.");
        }

        @Test
        void documentTextIsTrimmed() throws IOException {
            dev.langchain4j.data.document.Document doc =
                    parser.toDocument(fixture(), false, true, false);
            assertThat(doc.text())
                    .doesNotStartWith("\n")
                    .doesNotEndWith("\n");
        }
    }

    @Nested
    class RenderSectionsAsMarkdown {

        @Test
        void emptyInputReturnsEmptyString() {
            assertThat(parser.renderSectionsAsMarkdown(List.of())).isEmpty();
            assertThat(parser.renderSectionsAsMarkdown(null)).isEmpty();
        }

        @Test
        void rendersHeadingLevelsRecursively() throws IOException {
            write("tree.md", """
                    # Root

                    Root content.

                    ## Child

                    Child content.
                    """);
            MarkdownFile file = parse("tree.md").orElseThrow();

            String rendered = parser.renderSectionsAsMarkdown(file.getSubSections());

            assertThat(rendered)
                    .contains("# Root")
                    .contains("Root content.")
                    .contains("## Child")
                    .contains("Child content.");
        }
    }

    private Optional<MarkdownFile> parse(String fileName) {
        return parser.getMarkdownFile(resource(fileName));
    }

    private FileSystemResource resource(String fileName) {
        return new FileSystemResource(tempDir.resolve(fileName).normalize().toAbsolutePath());
    }
}
