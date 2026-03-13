package net.osgiliath.agentsdk.cucumber.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentHandoff;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentParsingSteps {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private AgentParser agentParser;

    private Path agentFilePath;
    private Agent parsedAgent;
    private Throwable stepError;

    @Before
    public void resetScenarioState() {
        agentFilePath = null;
        parsedAgent = null;
        stepError = null;
    }

    @Given("a sample agent file at {string}")
    public void aSampleAgentFileAt(String relativePath) {
        safely(() -> {
            agentFilePath = resolveFromProject(relativePath);
            assertThat(agentFilePath).exists();
        });
    }

    @When("the agent parser reads the headers")
    public void theAgentParserReadsTheHeaders() {
        safely(() -> parsedAgent = agentParser.getAgent(agentFilePath));
    }

    @When("the agent parser reads the content")
    public void theAgentParserReadsTheContent() {
        safely(() -> parsedAgent = agentParser.getAgent(agentFilePath));
    }

    @Then("the parsed agent name should be {string}")
    public void theParsedAgentNameShouldBe(String expectedName) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().name()).isEqualTo(expectedName);
    }

    @Then("the parsed agent description should contain {string}")
    public void theParsedAgentDescriptionShouldContain(String expectedSnippet) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().description()).contains(expectedSnippet);
    }

    @Then("the parsed argument hint should be {string}")
    public void theParsedArgumentHintShouldBe(String expectedHint) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().argumentHint()).isEqualTo(expectedHint);
    }

    @Then("the parsed tools should include:")
    public void theParsedToolsShouldInclude(List<String> expectedTools) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().tools()).containsAll(expectedTools);
    }

    @Then("the parsed user-invokable flag should be {string}")
    public void theParsedUserInvokableFlagShouldBe(String expectedValue) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().userInvokable()).isEqualTo(Boolean.parseBoolean(expectedValue));
    }

    @Then("the parsed disable-model-invocation flag should be {string}")
    public void theParsedDisableModelInvocationFlagShouldBe(String expectedValue) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().disableModelInvocation()).isEqualTo(Boolean.parseBoolean(expectedValue));
    }

    @Then("the parsed subagents should include:")
    public void theParsedSubagentsShouldInclude(List<String> expectedSubagents) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().subagents()).containsAll(expectedSubagents);
    }

    @Then("the parsed handoffs should contain a handoff:")
    public void theParsedHandoffsShouldContainAHandoff(List<String> handoffValues) {
        assertNoSetupError();
        assertThat(handoffValues).hasSize(4);
        AgentHandoff expected = new AgentHandoff(
            handoffValues.get(0),
            handoffValues.get(1),
            handoffValues.get(2),
            Boolean.parseBoolean(handoffValues.get(3))
        );
        assertThat(parsedAgent.headers().handoffs()).contains(expected);
    }

    @Then("the parsed skills should include:")
    public void theParsedSkillsShouldInclude(List<String> expectedSkills) {
        assertNoSetupError();
        assertThat(parsedAgent.headers().skills()).containsAll(expectedSkills);
    }

    @Then("the parsed content introduction should contain {string}")
    public void theParsedContentIntroductionShouldContain(String expectedSnippet) {
        assertNoSetupError();
        MarkdownSection root = rootSection();
        String normalizedExpected = expectedSnippet.replace("**", "");
        String normalizedActual = root.getContent().replace("**", "");
        assertThat(normalizedActual).contains(normalizedExpected);
    }

    @Then("the parsed content should include section {string}")
    public void theParsedContentShouldIncludeSection(String sectionTitle) {
        assertNoSetupError();
        assertThat(findSection(sectionTitle)).isPresent();
    }

    @Then("the section {string} should include subsection {string}")
    public void theSectionShouldIncludeSubsection(String sectionTitle, String subsectionTitle) {
        assertNoSetupError();
        MarkdownSection section = findSection(sectionTitle)
            .orElseThrow(() -> new AssertionError("Section not found: " + sectionTitle));
        assertThat(section.getSubSection(subsectionTitle)).isPresent();
    }

    private MarkdownSection rootSection() {
        List<MarkdownSection> sections = parsedAgent.content();
        assertThat(sections).isNotEmpty();
        return sections.getFirst();
    }

    private Optional<MarkdownSection> findSection(String sectionTitle) {
        for (MarkdownSection section : parsedAgent.content()) {
            if (sectionTitle.equals(section.getTitle())) {
                return Optional.of(section);
            }
            Optional<MarkdownSection> nested = section.getSubSection(sectionTitle);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private void assertNoSetupError() {
        assertThat(stepError).as("Given/When steps should complete without failures").isNull();
    }

    private void safely(ThrowingRunnable operation) {
        try {
            operation.run();
        } catch (Throwable throwable) {
            stepError = throwable;
        }
    }

    private Path resolveFromProject(String relativePath) {
        Path fromWorkDir = Path.of(relativePath);
        if (Files.exists(fromWorkDir)) {
            return fromWorkDir.toAbsolutePath().normalize();
        }
        try {
            var resource = getClass().getClassLoader().getResource(".");
            if (resource != null) {
                Path classesDir = Path.of(resource.toURI());
                Path moduleRoot = classesDir.getParent().getParent().getParent().getParent();
                Path candidate = moduleRoot.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
                Path projectRoot = moduleRoot.getParent();
                candidate = projectRoot.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        } catch (URISyntaxException e) {
            // fall through
        }
        return fromWorkDir.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
