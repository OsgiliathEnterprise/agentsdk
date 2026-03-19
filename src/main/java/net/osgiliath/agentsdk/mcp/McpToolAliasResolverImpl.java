package net.osgiliath.agentsdk.mcp;

import net.osgiliath.agentsdk.configuration.McpAliasesConfiguration;

import java.util.List;

/**
 * Default implementation of {@link McpToolAliasResolver} that looks up tool names
 * in the {@link McpAliasesConfiguration} alias map.
 */
public class McpToolAliasResolverImpl implements McpToolAliasResolver {

    private final McpAliasesConfiguration aliasesConfiguration;

    public McpToolAliasResolverImpl(McpAliasesConfiguration aliasesConfiguration) {
        this.aliasesConfiguration = aliasesConfiguration;
    }

    @Override
    public List<String> resolveToolNames(String toolName) {
        List<String> aliases = aliasesConfiguration.getAliases().get(toolName);
        return (aliases != null && !aliases.isEmpty()) ? List.copyOf(aliases) : List.of(toolName);
    }

    @Override
    public String resolvePrimaryToolName(String toolName) {
        return resolveToolNames(toolName).getFirst();
    }
}
