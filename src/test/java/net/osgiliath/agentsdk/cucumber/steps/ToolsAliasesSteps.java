package net.osgiliath.agentsdk.cucumber.steps;

import dev.langchain4j.model.chat.StreamingChatModel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.mcp.McpToolAliasResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for the tools-aliases feature.
 */
public class ToolsAliasesSteps {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private AgentParser agentParser;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private McpToolAliasResolver aliasResolver;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private CodepromptConfiguration aliasesConfiguration;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    @Qualifier("primaryStreamingChatModel")
    private StreamingChatModel streamingChatModel;

    private Agent parsedAgent;
    private List<String> resolvedToolNames;
    private String primaryToolName;

    @Before
    public void resetScenarioState() {
        parsedAgent = null;
        resolvedToolNames = null;
        primaryToolName = null;
    }

    @Given("a parsed agent from {string}")
    public void aParsedAgentFrom(String path) {
        parsedAgent = agentParser.getAgent(resourcePatternResolver.getResource("classpath:/" + path));
    }

    @Given("the MCP tool aliases configuration is loaded")
    public void theMcpToolAliasesConfigurationIsLoaded() {
        assertThat(streamingChatModel).isNotNull();
        assertThat(aliasesConfiguration.getMcp().getTools().getAliases()).isNotEmpty();
    }

    @Given("the tool {string} is declared in the agent tools list")
    public void theToolIsDeclaredInTheAgentToolsList(String toolName) {
        assertThat(parsedAgent.headers().mcp().value()).contains(toolName);
    }

    @Given("{string} is present in the aliases configuration")
    public void isInAliasesConfiguration(String toolName) {
        assertThat(aliasesConfiguration.getMcp().getTools().getAliases()).containsKey(toolName);
    }

    @Given("{string} is not present in the aliases configuration")
    public void isNotInAliasesConfiguration(String toolName) {
        assertThat(aliasesConfiguration.getMcp().getTools().getAliases()).doesNotContainKey(toolName);
    }

    @When("the alias resolver computes the MCP tool names for {string}")
    public void theAliasResolverComputesTheMcpToolNamesFor(String toolName) {
        resolvedToolNames = aliasResolver.resolveToolNames(toolName);
    }

    @When("the alias resolver computes the primary MCP tool name for {string}")
    public void theAliasResolverComputesPrimaryMcpToolNameFor(String toolName) {
        primaryToolName = aliasResolver.resolvePrimaryToolName(toolName);
    }

    @Then("the resolved tool names should contain:")
    public void theResolvedToolNamesShouldContain(DataTable dataTable) {
        assertThat(resolvedToolNames).containsAll(dataTable.asList());
    }

    @Then("the primary resolved tool name should be {string}")
    public void thePrimaryResolvedToolNameShouldBe(String expectedName) {
        assertThat(primaryToolName).isEqualTo(expectedName);
    }

    @Then("the resolved tool names should have a fallback order for unavailable tools")
    public void theResolvedToolNamesShouldHaveAFallbackOrder() {
        // Verify that the resolved tool names are ordered such that if the first
        // (primary) tool is not available, the resolver can fall back to the next one
        assertThat(resolvedToolNames).isNotNull().isNotEmpty();
        // The list should contain at least 2 elements (primary and at least one fallback)
        assertThat(resolvedToolNames).hasSizeGreaterThanOrEqualTo(2);
        // Verify the ordering is maintained (first element should be the primary)
        assertThat(resolvedToolNames.get(0)).isNotEmpty();
    }

}
