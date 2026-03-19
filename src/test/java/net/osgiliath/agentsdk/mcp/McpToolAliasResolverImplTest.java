package net.osgiliath.agentsdk.mcp;

import net.osgiliath.agentsdk.configuration.McpAliasesConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolAliasResolverImplTest {

    @Test
    void shouldReturnAllAliasesWhenToolIsConfigured() {
        McpToolAliasResolver resolver = resolverWith(Map.of(
            "list_directory", List.of("list_files_in_folder", "list_directory")
        ));

        assertThat(resolver.resolveToolNames("list_directory"))
            .containsExactly("list_files_in_folder", "list_directory");
    }

    @Test
    void shouldFallbackToOriginalToolWhenAliasIsMissing() {
        McpToolAliasResolver resolver = resolverWith(Map.of(
            "list_directory", List.of("list_files_in_folder", "list_directory")
        ));

        assertThat(resolver.resolveToolNames("git_checkout"))
            .containsExactly("git_checkout");
    }

    @Test
    void shouldReturnFirstAliasAsPrimaryToolName() {
        McpToolAliasResolver resolver = resolverWith(Map.of(
            "list_directory", List.of("list_files_in_folder", "list_directory")
        ));

        assertThat(resolver.resolvePrimaryToolName("list_directory"))
            .isEqualTo("list_files_in_folder");
    }

    private static McpToolAliasResolver resolverWith(Map<String, List<String>> aliases) {
        McpAliasesConfiguration configuration = new McpAliasesConfiguration();
        configuration.setAliases(aliases);
        return new McpToolAliasResolverImpl(configuration);
    }
}

