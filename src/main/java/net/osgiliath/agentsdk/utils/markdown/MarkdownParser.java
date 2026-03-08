package net.osgiliath.agentsdk.utils.markdown;

import dev.langchain4j.data.document.Document;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Interface for parsing markdown files and extracting structured content.
 */
public interface MarkdownParser {

    /**
     * Lists all markdown files in a given folder.
     *
     * @param folderPath the path to the folder to scan
     * @return list of markdown file names
     */
    List<Path> listMarkdownFiles(Path folderPath);

    /**
     * Retrieves the content of a markdown file and parses it into a structured format.
     *
     * @param folderPath the path to the folder containing the file
     * @param fileName the name of the markdown file to retrieve
     * @return an Optional containing the MarkdownFile object with headers and sections
     */
    Optional<MarkdownFile> getMarkdownFile(Path folderPath, String fileName);

    /**
     * Retrieves the headers from a markdown file.
     *
     * @param markdownFile the parsed markdown file
     * @return an Optional containing the MarkdownHeaders object
     */
    Optional<MarkdownHeaders> getHeaders(MarkdownFile markdownFile);

    /**
     * Retrieves the consolidated main sections from a markdown file.
     *
     * @param markdownFile the parsed markdown file
     * @return list of main sections
     */
    List<MarkdownSection> getMainSections(MarkdownFile markdownFile);

    /**
     * Retrieves the consolidated Sample sections from a markdown file.
     *
     * @param markdownFile the parsed markdown file
     * @return list of sample sections
     */
    List<MarkdownSection> getSampleSections(MarkdownFile markdownFile);

    /**
     * Retrieves the consolidated section or subsection with the specified name.
     *
     * @param markdownFile the parsed markdown file
     * @param sectionName the name of the section to retrieve
     * @return an Optional containing the section if found
     */
    Optional<MarkdownSection> getSection(MarkdownFile markdownFile, String sectionName);

    /**
     * Converts a markdown file to a LangChain4j Document.
     *
     * @param markdownFile the parsed markdown file
     * @param includeHeaders whether to include headers
     * @param includeSections whether to include sections
     * @param includeSamples whether to include samples
     * @return a Document containing the selected content
     */
    Document toDocument(MarkdownFile markdownFile, boolean includeHeaders, boolean includeSections, boolean includeSamples);
}
