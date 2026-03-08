package net.osgiliath.agentsdk.utils.markdown;

import java.util.List;
import java.util.Optional;

public interface MarkdownHeaders {
    /**
     * Returns a list of all header names defined in the markdown file.
     */
    List<String> headerKeys();
    /**
     * Returns the header value for a given header key.
     * @param headerKey The key representing the header (e.g., "tool", "description", "skills").
     * @return The header value associated with the provided header key.
     */
    Optional<Object> header(String headerKey);
}
