package net.osgiliath.agentsdk.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasAwareToolProviderComposerTest {

    @Test
    void shouldExposeLogicalToolNameAndFallbackToNextAliasWhenPrimaryFails() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();

        ToolProviderResult fullResult = ToolProviderResult.builder()
                .add(spec("list_directory"), failingExecutor("No working IDE endpoint available", primaryCalls))
                .add(spec("list_files_in_folder"), succeedingExecutor("ok", fallbackCalls))
                .build();

        AliasAwareToolProviderComposer composer = composerWithAliases(Map.of(
                "list_files_in_folder", List.of("list_directory", "list_files_in_folder")
        ));

        ToolProviderResult result = composer.compose(fullResult, List.of("list_files_in_folder"));

        ToolSpecification exposedTool = result.toolSpecificationByName("list_files_in_folder");
        assertThat(exposedTool).isNotNull();
        assertThat(exposedTool.name()).isEqualTo("list_files_in_folder");

        String executionResult = result.toolExecutorByName("list_files_in_folder")
                .execute(request("list_files_in_folder"), "memory");

        assertThat(executionResult).isEqualTo("ok");
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @Test
    void shouldSkipLogicalToolWhenNoAliasIsAvailable() {
        ToolProviderResult fullResult = ToolProviderResult.builder().build();
        AliasAwareToolProviderComposer composer = composerWithAliases(Map.of(
                "replace_file_text_by_path", List.of("edit_file", "replace_file_text_by_path")
        ));

        ToolProviderResult result = composer.compose(fullResult, List.of("replace_file_text_by_path"));

        assertThat(result.toolSpecificationByName("replace_file_text_by_path")).isNull();
        assertThat(result.toolExecutorByName("replace_file_text_by_path")).isNull();
    }

    @Test
    void shouldFailWhenAllAliasesFail() {
        ToolProviderResult fullResult = ToolProviderResult.builder()
                .add(spec("edit_file"), failingExecutor("calling \"tools/call\": No working IDE endpoint available.", new AtomicInteger()))
                .add(spec("replace_file_text_by_path"), failingExecutor("transport unavailable", new AtomicInteger()))
                .build();

        AliasAwareToolProviderComposer composer = composerWithAliases(Map.of(
                "replace_file_text_by_path", List.of("edit_file", "replace_file_text_by_path")
        ));

        ToolProviderResult result = composer.compose(fullResult, List.of("replace_file_text_by_path"));

        assertThatThrownBy(() -> result.toolExecutorByName("replace_file_text_by_path")
                .execute(request("replace_file_text_by_path"), "memory"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All aliases failed for logical tool 'replace_file_text_by_path'")
                .hasMessageContaining("edit_file")
                .hasMessageContaining("replace_file_text_by_path");
    }

    @Test
    void shouldReturnPrimaryErrorWithoutFallbackWhenFailureIsNotRetryable() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();

        ToolProviderResult fullResult = ToolProviderResult.builder()
                .add(spec("read_file"), failingExecutor("Error: Parent directory does not exist: /tmp/workspace/ai", primaryCalls))
                .add(spec("get_file_text_by_path"), succeedingExecutor("unexpected fallback success", fallbackCalls))
                .build();

        AliasAwareToolProviderComposer composer = composerWithAliases(Map.of(
                "get_file_text_by_path", List.of("read_file", "get_file_text_by_path")
        ));

        ToolProviderResult result = composer.compose(fullResult, List.of("get_file_text_by_path"));
        String executionResult = result.toolExecutorByName("get_file_text_by_path")
                .execute(request("get_file_text_by_path"), "memory");

        assertThat(executionResult).contains("Parent directory does not exist");
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isZero();
    }

    private static AliasAwareToolProviderComposer composerWithAliases(Map<String, List<String>> aliases) {
        CodepromptConfiguration properties = new CodepromptConfiguration();
        properties.getMcp().getTools().setAliases(aliases);
        McpToolAliasResolver resolver = new McpToolAliasResolverImpl(properties);
        return new AliasAwareToolProviderComposer(resolver);
    }

    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder()
                .name(name)
                .description("tool " + name)
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder()
                .id("id-1")
                .name(name)
                .arguments("{}")
                .build();
    }

    private static ToolExecutor failingExecutor(String message, AtomicInteger calls) {
        return (request, memoryId) -> {
            calls.incrementAndGet();
            throw new IllegalStateException(message);
        };
    }

    private static ToolExecutor succeedingExecutor(String result, AtomicInteger calls) {
        return (request, memoryId) -> {
            calls.incrementAndGet();
            return result;
        };
    }
}
