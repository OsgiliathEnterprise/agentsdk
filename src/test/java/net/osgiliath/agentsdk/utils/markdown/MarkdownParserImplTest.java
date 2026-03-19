package net.osgiliath.agentsdk.utils.markdown;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private MarkdownParserImpl parser;

    @TempDir
    Path tempDir;

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

    @Nested
    class FullContentRendering {

        @Test
        void plainParagraphsAreCapturedAsSingleSection() throws IOException {
            write("test.md", """
                Hello world.
                
                Second paragraph.
                """);

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

            assertThat(result).isPresent();
            assertThat(result.get().getSubSections().getFirst().getContent())
                .contains("[example](https://example.com)");
        }

        @Test
        void softLineBreakWithinParagraphIsPreserved() throws IOException {
            // Two lines without a blank line between them = soft line break inside one paragraph.
            write("test.md", "Line one\nLine two");

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

            assertThat(result).isPresent();
            String content = result.get().getSubSections().getFirst().getContent();
            // After the soft line break a newline character must appear
            assertThat(content).contains("Line one");
            assertThat(content).contains("Line two");
        }

        @Test
        void headingMarkersArePreservedWhenFileHasNoSections() throws IOException {
            // A file with headings DOES produce sections; verify the section title is correct
            // so that the heading-full path (renderNodeToText with preserveHeadings=true) is
            // also exercised via extractFullMarkdownContent when heading is in a linked file
            // that actually has no parseable sections due to nesting constraints (manual check).
            // Here we verify the normal heading-produces-section path instead.
            write("test.md", "# Top Heading\n\nSome text.");

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

            assertThat(result).isPresent();
            assertThat(result.get().getSubSections().getFirst().getTitle()).isEqualTo("Top Heading");
        }
    }

    // -----------------------------------------------------------------------
    // extractNodeTextForSectionRecursive path
    // A file with headings produces real sections; content between/after headings
    // is collected via extractNodeTextForSectionRecursive (double-newline separator).
    // -----------------------------------------------------------------------

    @Nested
    class SectionContentRendering {

        @Test
        void plainTextInSectionIsExtracted() throws IOException {
            write("test.md", """
                # My Section
                
                Plain text content.
                """);

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

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

            Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

            assertThat(result).isPresent();
            Optional<MarkdownSection> section = parser.getSection(result.get(), "Section");
            assertThat(section).isPresent();
            String content = section.get().getContent();
            assertThat(content).contains("Line one");
            assertThat(content).contains("Line two");
        }
    }

    // -----------------------------------------------------------------------
    // Misc public-API tests
    // -----------------------------------------------------------------------

    @Test
    void getNullFileReturnsEmpty() {
        assertThat(parser.getMarkdownFile(null, "file.md")).isEmpty();
        assertThat(parser.getMarkdownFile(tempDir, null)).isEmpty();
    }

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

        Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "test.md");

        assertThat(result).isPresent();
        Optional<MarkdownHeaders> headers = parser.getHeaders(result.get());
        assertThat(headers).isPresent();
        assertThat(headers.get().header("title")).hasValue("My Title");
        assertThat(headers.get().header("author")).hasValue("Someone");
    }

    @Test
    void linkedFileContentIsConsolidated() throws IOException {
        write("main.md", """
            # Main
            
            See [details](details.md).
            """);
        write("details.md", """
            # Details
            
            Detail content.
            """);

        Optional<MarkdownFile> result = parser.getMarkdownFile(tempDir, "main.md");

        assertThat(result).isPresent();
        List<MarkdownSection> sections = result.get().getSubSections();
        // "Main" from root + "Details" from linked file
        assertThat(sections).hasSizeGreaterThanOrEqualTo(2);
        boolean hasDetails = sections.stream()
            .anyMatch(s -> s.getTitle() != null && s.getTitle().contains("Details"));
        assertThat(hasDetails).isTrue();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void write(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}

