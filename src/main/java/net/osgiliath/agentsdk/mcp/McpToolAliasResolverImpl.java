package net.osgiliath.agentsdk.mcp;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of {@link McpToolAliasResolver} that looks up tool names
 * in {@link CodepromptConfiguration} MCP tool aliases.
 */
@Component
public class McpToolAliasResolverImpl implements McpToolAliasResolver {

    private final CodepromptConfiguration codepromptProperties;

    public McpToolAliasResolverImpl(CodepromptConfiguration codepromptProperties) {
        this.codepromptProperties = codepromptProperties;
    }

    @Override
    public List<String> resolveToolNames(String toolName) {
        List<String> aliases = codepromptProperties.getMcp().getTools().getAliases().get(toolName);
        return (aliases != null && !aliases.isEmpty()) ? List.copyOf(aliases) : List.of(toolName);
    }

    @Override
    public String resolvePrimaryToolName(String toolName) {
        return resolveToolNames(toolName).getFirst();
    }
}
