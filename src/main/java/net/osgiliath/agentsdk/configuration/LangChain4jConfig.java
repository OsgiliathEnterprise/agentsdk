package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for customizing LangChain4j HTTP client settings.
 *
 * This configuration defines a JdkHttpClientBuilder bean that configures the underlying HttpClient to use HTTP/1.1.
 * This is necessary for compatibility with LM Studio, which does not currently support HTTP/2.
 *
 * The langchain4j-spring-boot-starter will automatically detect this bean and use it when creating OpenAI (and other) chat models.
 */
@Configuration
@Profile("!github") // Only apply this configuration when the 'github' profile is NOT active
public class LangChain4jConfig {
    public static final String TOOL_PROVIDER_NONE = "toolProvider";
    private static final String CHAT_MODEL_HTTP_CLIENT_BUILDER = "openAiChatModelHttpClientBuilder";
    private static final String STREAMING_CHAT_MODEL_HTTP_CLIENT_BUILDER = "openAiStreamingChatModelHttpClientBuilder";
    public static final String TOOL_PROVIDER_FULL = "allTools";

    /**
     * Configures a JdkHttpClientBuilder that uses HTTP/1.1.
     * This is required for compatibility with LM Studio, which does not currently support HTTP/2.
     *
     * langchain4j-spring-boot-starter will automatically pick up this bean and use it
     * when creating OpenAI (and other) chat models.
     */
    @Bean(CHAT_MODEL_HTTP_CLIENT_BUILDER)
    public HttpClientBuilder jdkHttpClientBuilder(ObjectProvider<RestClient.Builder> restClientBuilder) {


        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);
        return JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);
    }

    @Bean(STREAMING_CHAT_MODEL_HTTP_CLIENT_BUILDER)
    public HttpClientBuilder jdkStreamingHttpClientBuilder(ObjectProvider<RestClient.Builder> restClientBuilder) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        return JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);
    }

    @Bean
    public McpTransport dockerGatewayMcpTransport() {
        return StdioMcpTransport.builder()
            .command(List.of("docker", "mcp", "gateway", "run"))
                .environment(Map.of("PATH", "/opt/homebrew/bin"))
            .logEvents(true) // only if you want to see the traffic in the log
            .build();
    }

    @Bean
    public McpClient mcpClient(McpTransport gatewayMcpTransport) {
        return DefaultMcpClient.builder()
                .key("GatewayClient")
            .transport(gatewayMcpTransport)
            .build();
    }

    @Bean(TOOL_PROVIDER_FULL)
    public McpToolProvider toolProviderFull(McpClient mcpClient) {
        return McpToolProvider.builder()
        .mcpClients(mcpClient)
        .build();
    }


    @Bean(TOOL_PROVIDER_NONE)
    @Primary
    public McpToolProvider toolProviderNo(McpClient mcpClient) {
        return McpToolProvider.builder()
        .mcpClients(mcpClient)
                .filter((client, spec) -> false) // filter out all tools, effectively disabling tool usage
        .build();
    }
}
