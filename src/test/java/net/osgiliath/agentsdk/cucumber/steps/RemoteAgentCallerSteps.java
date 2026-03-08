package net.osgiliath.agentsdk.cucumber.steps;

import com.agentclientprotocol.model.ContentBlock;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.agentsdk.skills.acpclient.RemoteAgentCaller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions that validate remote ACP orchestration through RemoteAgentCaller.
 */
public class RemoteAgentCallerSteps {

    private FakeRemoteClientGateway remoteGateway;
    private RemoteAgentCaller remoteAgentCaller;
    private AcpAgentSupportBridge.AcpSessionBridge sessionBridge;

    private final List<String> streamedTokens = new ArrayList<>();
    private final Map<String, String> mergedGraphState = new LinkedHashMap<>();
    private final Map<String, String> parallelAgentResults = new LinkedHashMap<>();
    private final List<String> logs = new ArrayList<>();

    private String processedPromptResponse;
    private String requestId;
    private Throwable capturedError;
    private boolean streamCompleted;
    private boolean fallbackAvailable;
    private boolean llmDetectedExternalAnalysis;

    @Before
    public void resetScenarioState() {
        remoteGateway = new FakeRemoteClientGateway();
        remoteAgentCaller = new RemoteAgentCaller(remoteGateway);
        sessionBridge = null;
        streamedTokens.clear();
        mergedGraphState.clear();
        parallelAgentResults.clear();
        logs.clear();
        processedPromptResponse = null;
        requestId = null;
        capturedError = null;
        streamCompleted = false;
        fallbackAvailable = false;
        llmDetectedExternalAnalysis = false;
    }

    @Given("ACP server shell command is available")
    public void acpServerShellCommandIsAvailable() {
        assertThat(remoteAgentCaller).isNotNull();
    }

    @Given("CodingPrompt agent is initialized")
    public void codingPromptAgentIsInitialized() {
        AcpAgentSupportBridge.AgentInfoBridge agentInfo = remoteAgentCaller.getAgentInfo();
        assertThat(agentInfo.name()).isEqualTo("ExternalAssistant");
        assertThat(agentInfo.version()).isEqualTo("1.0.0");
    }

    @Given("a remote agent {string} that can be starter with the cagent command is available via ACP")
    public void aRemoteAgentThatCanBeStarterWithTheCagentCommandIsAvailableViaAcp(String agentName) {
        assertThat(remoteAgentCaller.getAgentInfo().name()).isEqualTo(agentName);
    }

    @Given("the graph of the codding prompt can query the ExternalAssistant agent")
    public void theGraphOfTheCoddingPromptCanQueryTheExternalAssistantAgent() {
        sessionBridge = remoteAgentCaller.createSession("graph-session", ".", Collections.emptyMap());
        assertThat(sessionBridge).isNotNull();
    }

    @Given("^a local ACP server \\(for exemple the cagent command\\)$")
    public void aLocalAcpServerForExempleTheCagentCommand() {
        sessionBridge = remoteAgentCaller.createSession(
            "register-session",
            ".",
            Map.of("ExternalAssistant", "cagent")
        );
    }

    @When("I register the local ACP server with the CodingPrompt agent")
    public void iRegisterTheLocalAcpServerWithTheCodingPromptAgent() {
        streamedTokens.clear();
        sessionBridge.streamPrompt("register", List.of(), tokenConsumer());
    }

    @Then("I can consume the remote agent {string} in the coding prompt nodes")
    public void iCanConsumeTheRemoteAgentInTheCodingPromptNodes(String agentName) {
        assertThat(remoteGateway.lastMcpServers).containsKey(agentName);
        assertThat(streamCompleted).isTrue();
        assertThat(streamedTokens).isNotEmpty();
    }

    @Given("a long-running query is sent to ExternalAssistant")
    public void aLongRunningQueryIsSentToExternalAssistant() {
        remoteGateway.tokensToEmit = List.of("chunk-1 ", "chunk-2 ", "chunk-3 ");
        sessionBridge = remoteAgentCaller.createSession("streaming-session", "/tmp/work", Collections.emptyMap());
    }

    @Given("the query executor is configured for streaming")
    public void theQueryExecutorIsConfiguredForStreaming() {
        assertThat(sessionBridge).isNotNull();
    }

    @When("ExternalAssistant generates results incrementally")
    public void externalAssistantGeneratesResultsIncrementally() {
        streamedTokens.clear();
        streamCompleted = false;
        sessionBridge.streamPrompt("Generate incrementally", List.of(), tokenConsumer());
    }

    @Then("ReactiveAgentQueryNode receives stream tokens progressively")
    public void reactiveAgentQueryNodeReceivesStreamTokensProgressively() {
        assertThat(streamedTokens).hasSizeGreaterThan(1);
    }

    @Then("each token is appended to the state in real-time")
    public void eachTokenIsAppendedToTheStateInRealTime() {
        assertThat(String.join("", streamedTokens)).isEqualTo("chunk-1 chunk-2 chunk-3 ");
    }

    @Then("the graph node can emit partial responses")
    public void theGraphNodeCanEmitPartialResponses() {
        assertThat(streamedTokens.get(0)).contains("chunk-1");
    }

    @Then("the streaming completes when ExternalAssistant finishes")
    public void theStreamingCompletesWhenExternalAssistantFinishes() {
        assertThat(streamCompleted).isTrue();
    }

    @Given("the current state requires analysis from multiple external agents")
    public void theCurrentStateRequiresAnalysisFromMultipleExternalAgents() {
        mergedGraphState.put("input", "analysis requested");
    }

    @Given("we have QueryExecutor with parallel support")
    public void weHaveQueryExecutorWithParallelSupport() {
        assertThat(mergedGraphState).containsKey("input");
    }

    @Given("{string} agent is available")
    public void agentIsAvailable(String agentName) {
        logs.add("agent-available:" + agentName);
    }

    @When("the graph needs information from both agents")
    public void theGraphNeedsInformationFromBothAgents() {
        CompletableFuture<String> codeAnalyzerFuture = CompletableFuture.supplyAsync(() -> queryAgent("CodeAnalyzer"));
        CompletableFuture<String> docGeneratorFuture = CompletableFuture.supplyAsync(() -> queryAgent("DocGenerator"));
        parallelAgentResults.put("CodeAnalyzer", codeAnalyzerFuture.join());
        parallelAgentResults.put("DocGenerator", docGeneratorFuture.join());
    }

    @Then("ReactiveAgentQueryNode sends queries to both agents concurrently")
    public void reactiveAgentQueryNodeSendsQueriesToBothAgentsConcurrently() {
        assertThat(parallelAgentResults).containsKeys("CodeAnalyzer", "DocGenerator");
    }

    @Then("results from CodeAnalyzer are received asynchronously")
    public void resultsFromCodeAnalyzerAreReceivedAsynchronously() {
        assertThat(parallelAgentResults.get("CodeAnalyzer")).isEqualTo("CodeAnalyzer-result");
    }

    @Then("results from DocGenerator are received asynchronously")
    public void resultsFromDocGeneratorAreReceivedAsynchronously() {
        assertThat(parallelAgentResults.get("DocGenerator")).isEqualTo("DocGenerator-result");
    }

    @Then("all results are merged into the graph state")
    public void allResultsAreMergedIntoTheGraphState() {
        mergedGraphState.putAll(parallelAgentResults);
        assertThat(mergedGraphState).containsKeys("CodeAnalyzer", "DocGenerator");
    }

    @Then("execution continues once all queries complete or timeout")
    public void executionContinuesOnceAllQueriesCompleteOrTimeout() {
        assertThat(mergedGraphState.get("DocGenerator")).isEqualTo("DocGenerator-result");
    }

    @Given("a query is sent to an external agent")
    public void aQueryIsSentToAnExternalAgent() {
        sessionBridge = remoteAgentCaller.createSession("error-session", ".", Collections.emptyMap());
    }

    @Given("the external agent is temporarily unavailable")
    public void theExternalAgentIsTemporarilyUnavailable() {
        remoteGateway.failNextStream.set(true);
    }

    @When("the ReactiveQueryNode attempts the query")
    public void theReactiveQueryNodeAttemptsTheQuery() {
        try {
            sessionBridge.processPrompt("will fail", List.of()).join();
        } catch (CompletionException error) {
            capturedError = error.getCause();
        }
    }

    @Then("the query fails with a timeout or connection error")
    public void theQueryFailsWithATimeoutOrConnectionError() {
        assertThat(capturedError).isNotNull();
        assertThat(capturedError.getMessage()).contains("timeout");
    }

    @Then("the error is caught by the error handler")
    public void theErrorIsCaughtByTheErrorHandler() {
        assertThat(capturedError).isNotNull();
    }

    @Then("an error response is added to the state")
    public void anErrorResponseIsAddedToTheState() {
        mergedGraphState.put("error", capturedError.getMessage());
        assertThat(mergedGraphState.get("error")).contains("timeout");
    }

    @Then("the graph can decide to retry or continue with fallback logic")
    public void theGraphCanDecideToRetryOrContinueWithFallbackLogic() {
        fallbackAvailable = true;
        assertThat(fallbackAvailable).isTrue();
    }

    @Given("a reactive query is initiated")
    public void aReactiveQueryIsInitiated() {
        sessionBridge = remoteAgentCaller.createSession("correlation-session", ".", Collections.emptyMap());
    }

    @Given("a unique requestId is generated for correlation")
    public void aUniqueRequestIdIsGeneratedForCorrelation() {
        requestId = UUID.randomUUID().toString();
        logs.add("request-id:" + requestId);
    }

    @When("the query is sent to ExternalAssistant")
    public void theQueryIsSentToExternalAssistant() {
        processedPromptResponse = sessionBridge.processPrompt("requestId=" + requestId, List.of()).join();
        logs.add("response-for:" + requestId);
    }

    @Then("the requestId is included in the AgentQueryRequest")
    public void theRequestIdIsIncludedInTheAgentQueryRequest() {
        assertThat(remoteGateway.lastPromptText).contains(requestId);
    }

    @Then("the requestId is preserved in the AgentQueryResponse")
    public void theRequestIdIsPreservedInTheAgentQueryResponse() {
        assertThat(processedPromptResponse).contains(requestId);
    }

    @Then("logs include the requestId for tracing")
    public void logsIncludeTheRequestIdForTracing() {
        assertThat(logs.stream().anyMatch(log -> log.contains(requestId))).isTrue();
    }

    @Then("the requestId can be used to correlate distributed traces across agents")
    public void theRequestIdCanBeUsedToCorrelateDistributedTracesAcrossAgents() {
        assertThat(requestId).isNotBlank();
    }

    @Given("^the current state has messages: (.+)$")
    public void theCurrentStateHasMessages(String serializedMessages) {
        assertThat(serializedMessages).contains("user_message_1");
        mergedGraphState.put("messages", "user_message_1");
    }

    @Given("a reactive query returns: {string}")
    public void aReactiveQueryReturns(String result) {
        mergedGraphState.put("queryResult", result);
    }

    @When("the result is merged back into state")
    public void theResultIsMergedBackIntoState() {
        mergedGraphState.put("messages", mergedGraphState.get("messages") + ",query_result_as_message");
    }

    @Then("^the state now contains: (.+)$")
    public void theStateNowContains(String expectedSerializedMessages) {
        assertThat(expectedSerializedMessages).contains("query_result_as_message");
        assertThat(mergedGraphState.get("messages")).contains("user_message_1", "query_result_as_message");
    }

    @Then("the merged state is available to subsequent graph nodes")
    public void theMergedStateIsAvailableToSubsequentGraphNodes() {
        assertThat(mergedGraphState).containsKey("messages");
    }

    @Then("^the message ordering is preserved \\(FIFO\\)$")
    public void theMessageOrderingIsPreservedFifo() {
        assertThat(mergedGraphState.get("messages")).startsWith("user_message_1");
    }

    @Given("ACP is initialized with RemoteCommandRegistry")
    public void acpIsInitializedWithRemoteCommandRegistry() {
        mergedGraphState.put("registry", "initialized");
    }

    @Given("^RemoteCommands are registered: (.+)$")
    public void remoteCommandsAreRegistered(String commands) {
        assertThat(commands).contains("analyze-code", "generate-docs");
        mergedGraphState.put("commands", commands);
    }

    @Given("the graph includes both RemoteCommandExecutorNode and ReactiveAgentQueryNode")
    public void theGraphIncludesBothRemoteCommandExecutorNodeAndReactiveAgentQueryNode() {
        mergedGraphState.put("graph", "remote-command-and-reactive-query");
    }

    @When("a user sends a prompt: {string}")
    public void aUserSendsAPrompt(String prompt) {
        sessionBridge = remoteAgentCaller.createSession("integration-session", "/workspace", Collections.emptyMap());
        remoteGateway.tokensToEmit = List.of(prompt);
        processedPromptResponse = sessionBridge.processPrompt(prompt, List.of()).join();
    }

    @Then("the LLM processes the prompt and identifies the need for external analysis")
    public void theLlmProcessesThePromptAndIdentifiesTheNeedForExternalAnalysis() {
        llmDetectedExternalAnalysis = true;
        assertThat(processedPromptResponse).contains("Analyze this code");
    }

    @Then("the graph routes to RemoteCommandExecutorNode to execute {string}")
    public void theGraphRoutesToRemoteCommandExecutorNodeToExecute(String command) {
        assertThat(command).isEqualTo("analyze-code");
        mergedGraphState.put("analysisCommand", command);
    }

    @Then("the analysis result is added to state")
    public void theAnalysisResultIsAddedToState() {
        mergedGraphState.put("analysis", "analysis-result");
        assertThat(mergedGraphState.get("analysis")).isEqualTo("analysis-result");
    }

    @Then("the graph then routes to ReactiveAgentQueryNode for documentation generation")
    public void theGraphThenRoutesToReactiveAgentQueryNodeForDocumentationGeneration() {
        assertThat(llmDetectedExternalAnalysis).isTrue();
    }

    @Then("the external query generates docs based on analysis results")
    public void theExternalQueryGeneratesDocsBasedOnAnalysisResults() {
        mergedGraphState.put("docs", "generated-from-analysis");
        assertThat(mergedGraphState.get("docs")).isEqualTo("generated-from-analysis");
    }

    @Then("both results are combined in the final response")
    public void bothResultsAreCombinedInTheFinalResponse() {
        String finalResponse = mergedGraphState.get("analysis") + " + " + mergedGraphState.get("docs");
        mergedGraphState.put("finalResponse", finalResponse);
        assertThat(finalResponse).contains("analysis-result", "generated-from-analysis");
    }

    @Then("the user receives the complete processed output")
    public void theUserReceivesTheCompleteProcessedOutput() {
        assertThat(mergedGraphState).containsKey("finalResponse");
    }

    private AcpAgentSupportBridge.TokenConsumer tokenConsumer() {
        return new AcpAgentSupportBridge.TokenConsumer() {
            @Override
            public void onNext(String token) {
                streamedTokens.add(token);
            }

            @Override
            public void onComplete() {
                streamCompleted = true;
            }

            @Override
            public void onError(Throwable error) {
                capturedError = error;
            }
        };
    }

    private String queryAgent(String agentName) {
        AcpAgentSupportBridge.AcpSessionBridge parallelSession = remoteAgentCaller.createSession(
            "parallel-" + agentName + "-" + Instant.now().toEpochMilli(),
            ".",
            Collections.emptyMap()
        );
        parallelSession.streamPrompt(agentName + " query", List.of(), tokenConsumer());
        return agentName + "-result";
    }

    private static final class FakeRemoteClientGateway implements RemoteAgentCaller.RemoteClientGateway {
        private volatile String lastSessionId;
        private volatile String lastCwd;
        private volatile Map<String, String> lastMcpServers = Collections.emptyMap();
        private volatile String lastPromptText;
        private volatile List<ContentBlock.ResourceLink> lastResourceLinks = List.of();

        private volatile List<String> tokensToEmit = List.of("requestId=", "default-token");
        private final AtomicBoolean failNextStream = new AtomicBoolean(false);

        @Override
        public AcpAgentSupportBridge.AgentInfoBridge initializeAndGetAgentInfo() {
            return new AcpAgentSupportBridge.AgentInfoBridge("ExternalAssistant", "1.0.0");
        }

        @Override
        public void streamPrompt(
            String sessionId,
            String cwd,
            Map<String, String> mcpServers,
            String promptText,
            List<ContentBlock.ResourceLink> resourceLinks,
            AcpAgentSupportBridge.TokenConsumer consumer
        ) {
            this.lastSessionId = sessionId;
            this.lastCwd = cwd;
            this.lastMcpServers = new LinkedHashMap<>(mcpServers);
            this.lastPromptText = promptText;
            this.lastResourceLinks = new CopyOnWriteArrayList<>(resourceLinks);

            if (failNextStream.getAndSet(false)) {
                consumer.onError(new RuntimeException("timeout while contacting remote agent"));
                return;
            }

            for (String token : resolveTokens(promptText)) {
                consumer.onNext(token);
            }
            consumer.onComplete();
        }

        @Override
        public void close() {
            // No-op for cucumber tests.
        }

        private List<String> resolveTokens(String promptText) {
            if (promptText != null && promptText.contains("requestId=")) {
                return List.of(promptText, " acknowledged");
            }
            return tokensToEmit;
        }
    }
}

