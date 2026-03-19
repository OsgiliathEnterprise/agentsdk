package net.osgiliath.agentsdk.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "codeprompt.mcp.tools")
public class McpAliasesConfiguration {

    private Map<String, List<String>> aliases = new HashMap<>();

    public Map<String, List<String>> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, List<String>> aliases) {
        this.aliases = aliases;
    }
}
