package net.osgiliath.agentsdk.mcp;

import java.util.List;

/**
 * Resolves MCP tool names declared in agent or skill metadata to the actual tool names
 * to use when calling MCP servers, applying the configured aliases.
 *
 * <p>When an agent declares a logical tool name (e.g. {@code list_directory}), the
 * aliases configuration may map it to one or more actual MCP tool identifiers
 * (e.g. {@code [list_files_in_folder, list_directory]}).  If no alias is registered
 * the original name is returned unchanged.
 */
public interface McpToolAliasResolver {

    /**
     * Returns all MCP tool names to use for the given logical tool name.
     *
     * <p>If the tool name is present in the aliases configuration, the full list of
     * alias values is returned.  Otherwise a singleton list containing the original
     * tool name is returned.
     *
     * @param toolName the logical tool name as declared in agent/skill metadata
     * @return the list of actual MCP tool names to use (never {@code null} or empty)
     */
    List<String> resolveToolNames(String toolName);

    /**
     * Returns the primary (first) MCP tool name for the given logical tool name.
     *
     * <p>If aliases are configured the first alias value is returned; otherwise the
     * original tool name is returned.
     *
     * @param toolName the logical tool name as declared in agent/skill metadata
     * @return the primary actual MCP tool name to call (never {@code null})
     */
    String resolvePrimaryToolName(String toolName);
}

