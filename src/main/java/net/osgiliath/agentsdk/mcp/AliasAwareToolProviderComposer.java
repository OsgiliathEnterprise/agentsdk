package net.osgiliath.agentsdk.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a composite {@link ToolProviderResult} that exposes logical tool names while
 * executing the configured aliases in order with fallback on runtime failures.
 */
@Component
public class AliasAwareToolProviderComposer {

    private static final Logger log = LoggerFactory.getLogger(AliasAwareToolProviderComposer.class);

    private final McpToolAliasResolver aliasResolver;

    public AliasAwareToolProviderComposer(McpToolAliasResolver aliasResolver) {
        this.aliasResolver = Objects.requireNonNull(aliasResolver, "aliasResolver must not be null");
    }

    public ToolProviderResult compose(ToolProviderResult fullResult, List<String> logicalToolNames) {
        Objects.requireNonNull(fullResult, "fullResult must not be null");
        Objects.requireNonNull(logicalToolNames, "logicalToolNames must not be null");
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        Set<String> immediateReturnToolNames = new LinkedHashSet<>();

        for (String logicalToolName : logicalToolNames) {
            List<String> aliases = aliasResolver.resolveToolNames(logicalToolName);
            List<String> availableAliases = aliases.stream()
                    .filter(alias -> fullResult.toolSpecificationByName(alias) != null
                            && fullResult.toolExecutorByName(alias) != null)
                    .toList();

            if (availableAliases.isEmpty()) {
                continue;
            }

            ToolSpecification primaryAliasSpec = fullResult.toolSpecificationByName(availableAliases.getFirst());
            ToolSpecification logicalSpec = primaryAliasSpec.toBuilder()
                    .name(logicalToolName)
                    .build();

            builder.add(logicalSpec, new AliasFallbackToolExecutor(logicalToolName, availableAliases, fullResult));
            if (availableAliases.stream().anyMatch(fullResult.immediateReturnToolNames()::contains)) {
                immediateReturnToolNames.add(logicalToolName);
            }
        }

        return builder.immediateReturnToolNames(immediateReturnToolNames).build();
    }

    private static final class AliasFallbackToolExecutor implements ToolExecutor {

        private final String logicalToolName;
        private final List<String> aliases;
        private final ToolProviderResult fullResult;

        private AliasFallbackToolExecutor(String logicalToolName, List<String> aliases, ToolProviderResult fullResult) {
            this.logicalToolName = Objects.requireNonNull(logicalToolName, "logicalToolName must not be null");
            this.aliases = Objects.requireNonNull(aliases, "aliases must not be null");
            this.fullResult = Objects.requireNonNull(fullResult, "fullResult must not be null");
            if (aliases.isEmpty()) {
                throw new IllegalArgumentException("aliases must not be empty");
            }
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            Objects.requireNonNull(request, "request must not be null");
            RuntimeException lastFailure = null;
            List<String> failures = new ArrayList<>();

            for (String alias : aliases) {
                ToolExecutor aliasExecutor = fullResult.toolExecutorByName(alias);
                if (aliasExecutor == null) {
                    continue;
                }

                ToolExecutionRequest aliasRequest = request.toBuilder().name(alias).build();
                try {
                    return aliasExecutor.execute(aliasRequest, memoryId);
                } catch (RuntimeException ex) {
                    String errorMessage = safeMessage(ex);
                    log.debug("Alias '{}' failed for logical tool '{}': {}", alias, logicalToolName, errorMessage);

                    if (!isRetryableAliasFailure(errorMessage)) {
                        // Deterministic tool-domain errors should be surfaced to the model directly
                        // so it can adapt (e.g. create missing folders) without switching provider.
                        return "Error: " + errorMessage;
                    }

                    lastFailure = ex;
                    failures.add(alias + ": " + errorMessage);
                }
            }

            if (lastFailure != null) {
                throw new IllegalStateException(
                        "All aliases failed for logical tool '" + logicalToolName + "': " + failures,
                        lastFailure);
            }
            throw new IllegalStateException(
                    "No available executors for logical tool '" + logicalToolName + "' and aliases " + aliases);
        }

        private static String safeMessage(RuntimeException ex) {
            String message = ex.getMessage();
            return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
        }

        private static boolean isRetryableAliasFailure(String errorMessage) {
            String normalized = errorMessage.toLowerCase(Locale.ROOT);

            boolean deterministicDomainError = normalized.contains("enoent")
                    || normalized.contains("parent directory does not exist")
                    || normalized.contains("no such file or directory")
                    || normalized.contains("not a directory")
                    || normalized.contains("is a directory")
                    || normalized.contains("permission denied")
                    || normalized.contains("invalid path")
                    || normalized.contains("invalid argument");
            if (deterministicDomainError) {
                return false;
            }

            return normalized.contains("no working ide endpoint available")
                    || normalized.contains("calling \"tools/call\"")
                    || normalized.contains("connection refused")
                    || normalized.contains("timed out")
                    || normalized.contains("timeout")
                    || normalized.contains("transport")
                    || normalized.contains("unavailable")
                    || normalized.contains("failed to initialize");
        }
    }
}
