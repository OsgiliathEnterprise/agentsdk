Feature: Agent Parsing

  Background:
    Given a sample agent file at "./src/test/resources/dataset/markdown/skills/sample-agent/sample-agent.md"

  Scenario: Should parse name
    When the agent parser reads the headers
    Then the parsed agent name should be "Code Review Agent"

  Scenario: Should parse description
    When the agent parser reads the headers
    Then the parsed agent description should contain "Reviews code changes for quality"

  Scenario: Should parse argument hint
    When the agent parser reads the headers
    Then the parsed argument hint should be "[file or code to review]"

  Scenario: Should parse Tools
    When the agent parser reads the headers
    Then the parsed tools should include:
      | read   |
      | search |

  Scenario: Should parse user-invokable
    When the agent parser reads the headers
    Then the parsed user-invokable flag should be "true"

  Scenario: Should parse disable-model-invocation
    When the agent parser reads the headers
    Then the parsed disable-model-invocation flag should be "false"

  Scenario: Should parse subagents
    When the agent parser reads the headers
    Then the parsed subagents should include:
      | subagent-1 |
      | subagent-2 |
      | ...        |

  Scenario: Should parse handoffs
    When the agent parser reads the headers
    Then the parsed handoffs should contain a handoff:
      | Hand off to Backend |
      | subagent-1 |
      | Continue working on the backend for this task. |
      | false |

  Scenario: Should parse Skills
    When the agent parser reads the headers
    Then the parsed skills should include:
      | Security Analysis |
      | Code Quality Assessment |
      | Performance Optimization |
      | Best Practices Enforcement |

  Scenario: Should parse introduction
    When the agent parser reads the content
    Then the parsed content introduction should contain "You are the **Code Review Agent**"

  Scenario: Should parse subsections
    When the agent parser reads the content
    Then the parsed content should include section "Responsibilities"
    And the parsed content should include section "Rules (mandatory read)"

  Scenario: Should nest subsections
    When the agent parser reads the content
    Then the section "Rules (mandatory read)" should include subsection "The Cardinal Rule"
    And the section "Rules (mandatory read)" should include subsection "Must follow Rules, raise flags if you need to break them"

