package net.osgiliath.agentsdk.agent.parser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.agentsdk.common.parsing.MarkdownContentSections;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.mcp.AliasAwareToolProviderComposer;
import net.osgiliath.agentsdk.mcp.McpToolAliasResolverImpl;
import net.osgiliath.agentsdk.skills.converter.MarkdownSkillsToLangChainSkillConverter;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillsHeaders;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatRequestBuilderTest {

    private AgentParser agentParser;
    private McpToolProvider fullToolProvider;
    private CodepromptConfiguration configuration;
    private AliasAwareToolProviderComposer aliasAwareToolProviderComposer;
    private MarkdownSkillsToLangChainSkillConverter markdownSkillConverter;
    private SkillResolver skillResolver;
    private AgentChatRequestBuilder builder;

    @BeforeEach
    void setUp() {
        agentParser = mock(AgentParser.class);
        fullToolProvider = mock(McpToolProvider.class);
        configuration = new CodepromptConfiguration();
        aliasAwareToolProviderComposer = new AliasAwareToolProviderComposer(new McpToolAliasResolverImpl(configuration));
        markdownSkillConverter = mock(MarkdownSkillsToLangChainSkillConverter.class);
        skillResolver = mock(SkillResolver.class);
        when(skillResolver.resolveSkills(any())).thenReturn(List.of());
        builder = new AgentChatRequestBuilder(agentParser, fullToolProvider, aliasAwareToolProviderComposer, markdownSkillConverter, skillResolver);
    }

    @Test
    void shouldExposeDeclaredToolsAndFilterOutUndeclaredTools() {
        Agent agent = newAgentWithTools(List.of("read", "run_in_terminal"));
        configuration.getMcp().getTools().setAliases(Map.of("read", List.of("read_file", "grep_search")));

        ToolProviderResult fullResult = toolProviderResult("read_file", "grep_search", "run_in_terminal", "insert_edit_into_file");
        when(fullToolProvider.provideTools(any(ToolProviderRequest.class))).thenReturn(fullResult);

        ToolProviderResult filtered = builder.buildToolProviderResult(
                agent,
                UserMessage.from("show files"),
                "session-42",
                new InvocationParameters());

        assertThat(filtered.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("read", "run_in_terminal");
        verify(fullToolProvider).provideTools(any(ToolProviderRequest.class));
    }

    @Test
    void shouldBuildChatRequestWithSystemAndUserMessagesAndFilteredTools() {
        Agent agent = newAgentWithTools(List.of("read"));
        when(agentParser.getSystemPrompt(agent)).thenReturn(SystemMessage.from("system prompt"));
        when(fullToolProvider.provideTools(any(ToolProviderRequest.class)))
                .thenReturn(toolProviderResult("read", "insert_edit_into_file"));

        UserMessage userMessage = UserMessage.from("list files");
        ChatRequest chatRequest = builder.buildChatRequest(agent, userMessage, "session-7", new InvocationParameters());

        assertThat(chatRequest.messages())
                .extracting(ChatMessage::type)
                .containsExactly(SystemMessage.from("s").type(), userMessage.type());
        assertThat(((SystemMessage) chatRequest.messages().getFirst()).text()).isEqualTo("system prompt");
        assertThat(chatRequest.messages().get(1)).isEqualTo(userMessage);
        assertThat(chatRequest.toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("read");
    }

    @Test
    void shouldExposeSkillsViaActivateSkillToolInsteadOfInliningSkillContent() {
        Skill markdownSkill = markdownSkill("memory-skill");
        Agent agent = newAgentWithToolsAndSkills(List.of("read"), List.of(markdownSkill));
        when(agentParser.getSystemPrompt(agent)).thenReturn(SystemMessage.from("system prompt"));
        when(skillResolver.resolveSkills(agent.getSkillsName())).thenReturn(List.of(markdownSkill));
        when(fullToolProvider.provideTools(any(ToolProviderRequest.class)))
                .thenReturn(toolProviderResult("read", "insert_edit_into_file"));
        when(markdownSkillConverter.convert(markdownSkill)).thenReturn(dev.langchain4j.skills.Skill.builder()
                .name("memory-skill")
                .description("Keeps project memory consistent")
                .content("FULL SKILL CONTENT")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder().name("memory_tool").description("memory tool").build(), mock(ToolExecutor.class))
                        .build())
                .build());

        ChatRequest chatRequest = builder.buildChatRequest(agent, UserMessage.from("resync project"), "session-8", new InvocationParameters());

        assertThat(((SystemMessage) chatRequest.messages().getFirst()).text())
                .contains("system prompt")
                .contains("<available_skills>")
                .contains("<name>memory-skill</name>")
                .doesNotContain("FULL SKILL CONTENT");
        assertThat(chatRequest.toolSpecifications())
                .extracting(ToolSpecification::name)
                .contains("read", "activate_skill")
                .doesNotContain("memory_tool");
    }

    @Test
    void shouldExposeSkillScopedToolsAfterSkillActivation() {
        Skill markdownSkill = markdownSkill("memory-skill");
        Agent agent = newAgentWithToolsAndSkills(List.of(), List.of(markdownSkill));
        when(agentParser.getSystemPrompt(agent)).thenReturn(SystemMessage.from("system prompt"));
        when(skillResolver.resolveSkills(agent.getSkillsName())).thenReturn(List.of(markdownSkill));
        when(markdownSkillConverter.convert(markdownSkill)).thenReturn(dev.langchain4j.skills.Skill.builder()
                .name("memory-skill")
                .description("Keeps project memory consistent")
                .content("FULL SKILL CONTENT")
                .toolProviders(request -> ToolProviderResult.builder()
                        .add(ToolSpecification.builder().name("memory_tool").description("memory tool").build(), mock(ToolExecutor.class))
                        .build())
                .build());

        UserMessage userMessage = UserMessage.from("resync project");
        ChatRequest initialRequest = builder.buildChatRequest(agent, userMessage, "session-9", new InvocationParameters());
        ToolProviderResult initialTools = builder.buildToolProviderResult(
                agent, userMessage, "session-9", new InvocationParameters(), initialRequest.messages());

        dev.langchain4j.agent.tool.ToolExecutionRequest activateRequest = dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id("activate-1")
                .name("activate_skill")
                .arguments("{\"skill_name\":\"memory-skill\"}")
                .build();
        ToolExecutionResult activationResult = initialTools.toolExecutorByName("activate_skill")
                .executeWithContext(activateRequest, InvocationContext.builder()
                        .chatMemoryId("session-9")
                        .invocationParameters(new InvocationParameters())
                        .timestampNow()
                        .build());

        List<ChatMessage> activatedMessages = new ArrayList<>(initialRequest.messages());
        activatedMessages.add(ToolExecutionResultMessage.builder()
                .id(activateRequest.id())
                .toolName(activateRequest.name())
                .text(activationResult.resultText())
                .attributes(activationResult.attributes())
                .isError(activationResult.isError())
                .build());

        ToolProviderResult activatedTools = builder.buildToolProviderResult(
                agent, userMessage, "session-9", new InvocationParameters(), activatedMessages);

        assertThat(initialTools.toolSpecificationByName("activate_skill")).isNotNull();
        assertThat(initialTools.toolSpecificationByName("memory_tool")).isNull();
        assertThat(activatedTools.toolSpecificationByName("memory_tool")).isNotNull();
    }

    private Agent newAgentWithTools(List<String> tools) {
        return newAgentWithToolsAndSkills(tools, List.of());
    }

    private Agent newAgentWithToolsAndSkills(List<String> tools, List<Skill> skills) {
        List<String> skillNames = skills.stream().map(Skill::getName).toList();
        AgentHeaders headers = new AgentHeaders(
                "Test Agent",
                "Agent used for unit tests",
                "",
                tools,
                List.of(LLMS_KIND.MINI),
                true,
                false,
                List.of(),
                List.of(),
                skillNames);

        List<SkillsHeaders> skillHeaders = skills.stream()
                .map(Skill::headers)
                .toList();

        return new Agent(headers, new MarkdownContentSections(List.of()), skillHeaders);
    }

    private Skill markdownSkill(String name) {
        return new Skill(
                new SkillsHeaders(name, "Skill for unit tests", List.of(), List.of("memory_tool"), List.of()),
                List.of(),
                List.of(),
                List.of(),
                new MarkdownContentSections(List.of()),
                List.of()
        );
    }

    private ToolProviderResult toolProviderResult(String... names) {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        for (String name : names) {
            tools.put(ToolSpecification.builder().name(name).description(name).build(), mock(ToolExecutor.class));
        }
        return new ToolProviderResult(tools);
    }
}

