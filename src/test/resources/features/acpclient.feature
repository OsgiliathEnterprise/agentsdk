Feature: Remote Agent Commands Registration and Reactive Querying in ACP

  Background:
    Given ACP server shell command is available
    And CodingPrompt agent is initialized
    And a remote agent "ExternalAssistant" that can be starter with the cagent command is available via ACP
    And the graph of the codding prompt can query the ExternalAssistant agent

  @scenario1
  Scenario: register ACP servers
    Given a local ACP server (for exemple the cagent command)
    When I register the local ACP server with the CodingPrompt agent
    Then I can consume the remote agent "ExternalAssistant" in the coding prompt nodes

  @scenario2 @reactive-query @streaming
  Scenario: Reactive query results are streamed incrementally
    Given a long-running query is sent to ExternalAssistant
    And the query executor is configured for streaming

    When ExternalAssistant generates results incrementally
    Then ReactiveAgentQueryNode receives stream tokens progressively
    And each token is appended to the state in real-time
    And the graph node can emit partial responses
    And the streaming completes when ExternalAssistant finishes

  @scenario2 @reactive-query @multiple-agents
  Scenario: Multiple reactive queries can be executed in parallel
    Given the current state requires analysis from multiple external agents
    And we have QueryExecutor with parallel support
    And "CodeAnalyzer" agent is available
    And "DocGenerator" agent is available

    When the graph needs information from both agents
    Then ReactiveAgentQueryNode sends queries to both agents concurrently
    And results from CodeAnalyzer are received asynchronously
    And results from DocGenerator are received asynchronously
    And all results are merged into the graph state
    And execution continues once all queries complete or timeout

  @scenario2 @reactive-query @error-handling
  Scenario: Handle errors in reactive agent queries
    Given a query is sent to an external agent
    And the external agent is temporarily unavailable

    When the ReactiveQueryNode attempts the query
    Then the query fails with a timeout or connection error
    And the error is caught by the error handler
    And an error response is added to the state
    And the graph can decide to retry or continue with fallback logic

  @scenario2 @reactive-query @correlation
  Scenario: Query correlation IDs are tracked for distributed tracing
    Given a reactive query is initiated
    And a unique requestId is generated for correlation

    When the query is sent to ExternalAssistant
    Then the requestId is included in the AgentQueryRequest
    And the requestId is preserved in the AgentQueryResponse
    And logs include the requestId for tracing
    And the requestId can be used to correlate distributed traces across agents

  @scenario2 @reactive-query @state-merge
  Scenario: Query results are correctly merged into graph state
    Given the current state has messages: ["user_message_1"]
    And a reactive query returns: "query_result_data"

    When the result is merged back into state
    Then the state now contains: ["user_message_1", "query_result_as_message"]
    And the merged state is available to subsequent graph nodes
    And the message ordering is preserved (FIFO)

  @integration
  Scenario: Full workflow - Command registration + Reactive query execution
    Given ACP is initialized with RemoteCommandRegistry
    And RemoteCommands are registered: ["analyze-code", "generate-docs"]
    And the graph includes both RemoteCommandExecutorNode and ReactiveAgentQueryNode

    When a user sends a prompt: "Analyze this code and generate its documentation"
    Then the LLM processes the prompt and identifies the need for external analysis
    And the graph routes to RemoteCommandExecutorNode to execute "analyze-code"
    And the analysis result is added to state
    And the graph then routes to ReactiveAgentQueryNode for documentation generation
    And the external query generates docs based on analysis results
    And both results are combined in the final response
    And the user receives the complete processed output
