# Agent SDK
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=OsgiliathEnterprise_agentsdk&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=OsgiliathEnterprise_agentsdk)
![Maven Central Version](https://img.shields.io/maven-central/v/net.osgiliath.ai/agent-sdk)

A Spring Boot library that helps you build ACP-aware LLM agents with **LangChain4j** and **LangGraph4j**. It provides reusable parsing, rendering, and ACP client utilities so applications can define agents/skills in Markdown and invoke remote STDIO agents.

## Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                 agent-sdk                                   │
│                                                                              │
│  Markdown Assets                      Runtime Integrations                   │
│  ┌─────────────────────────────┐      ┌──────────────────────────────────┐   │
│  │ AgentParser / SkillParser   │      │ RemoteAcpClient (Kotlin)        │   │
│  │ SkillRenderer               │─────►│ OutAcpAdapter / RemoteAgentCaller│   │
│  │ CommonMark + custom model   │      │ ACP protocol client path         │   │
│  └───────────────┬─────────────┘      └──────────────────┬───────────────┘   │
│                  │                                         │                   │
│                  ▼                                         ▼                   │
│          Agent + Skill definitions               LLM -> MCP edges             │
│          (front matter + sections)               (LangGraph4j integration)    │
└──────────────────────────────────────────────────────────────────────────────┘
```

The SDK is organized around three responsibilities:

| Layer | Main package | Responsibility |
|---|---|---|
| **Definition model** | `agent.parser`, `skills.parser`, `utils.markdown` | Parse Markdown agent/skill files into typed Java objects. |
| **Execution bridge** | `skills.acpclient` (Java + Kotlin) | Call remote ACP-compatible STDIO agents and adapt responses. |
| **Graph integration** | `langgraph.edge` | Provide edges/helpers to connect LLM output to MCP/tool flows. |

## Key Components

### `AgentParser` / `AgentParserImpl`

Parses agent definition Markdown into structured metadata and content blocks (`Agent`, headers, sections, handoff metadata).

### `SkillParser` / `SkillParserImpl`

Parses skill documents and headers (name, description, dependencies, MCP/LLM hints) into typed models used by runtime orchestration.

### `SkillRenderer` / `SkillRendererImpl`

Renders parsed skills back into textual prompts/assets suitable for model consumption.

### `RemoteAcpClient` (Kotlin) and `RemoteAgentCaller` (Java)

Implements outbound ACP client behavior so an SDK consumer can invoke a remote STDIO agent and integrate responses in its own agent flow.

### MCP server aliases (per session)

Remote ACP sessions can now carry an **MCP alias registry** through `createSession(sessionId, cwd, mcpServers)`.

- `mcpServers` is a per-session map of **alias name -> server definition**.
- The alias key is the name exposed to the remote ACP session.
- With the default SDK client, each entry is converted to `McpServer.Stdio(name=<alias>, command=<value>, args=[], env=[])`.
- `cwd` defaults to `"."` when omitted or blank, and `null` MCP maps are normalized to an empty map.
- If you plug in a custom `RemoteClientGateway`, the map is forwarded unchanged.

This is useful when your host agent wants to expose short, stable names such as `ExternalAssistant` or `repo-tools` to a downstream ACP session without hard-coding those aliases into prompt text.

> [!NOTE]
> In the current default implementation, alias values are modeled as **single stdio command strings**. Per-alias arguments and environment variables are not expanded yet, so if you need a richer launch shape you should wrap it in a script/launcher command or provide a custom `RemoteClientGateway`.

Example:

```java
OutAcpAdapter.AcpSessionBridge session = remoteAgentCaller.createSession(
    "register-session",
    ".",
    Map.of("ExternalAssistant", "cagent")
);
```

In this example, `ExternalAssistant` is the alias visible inside the remote ACP session, while `cagent` is the stdio command launched by the default client implementation.

Session context propagation is covered by the SDK tests, including forwarding of `sessionId`, `cwd`, and `mcpServers` to the remote gateway.

### Spring property configuration for MCP tool aliases

The SDK also supports **tool-name aliases** through Spring Boot configuration. This is separate from the per-session `mcpServers` map above:

- `createSession(..., mcpServers)` configures **remote MCP servers** available to a session.
- `codeprompt.mcp.tools.aliases` configures **logical tool names** that should be translated before LangChain4j calls an MCP tool.

The Spring binding is defined under the prefix `codeprompt.mcp.tools` and binds `aliases` into a `Map<String, List<String>>`.

Example:

```yaml
codeprompt:
  mcp:
    tools:
      aliases:
        list_directory:
          - list_files_in_folder
          - list_directory
        directory_tree:
          - list_directory_tree_in_folder
          - directory_tree
        get-library-docs:
          - get_library_docs
```

How this works:

- Each key under `aliases` is the **logical tool name** used by your agent or skill metadata.
- Each list contains one or more **actual MCP tool names** exposed by the target MCP client/server.
- The resolver keeps the full list available, but the current LangChain4j integration uses the **first item as the primary mapped tool name**.
- If a tool name has no configured alias entry, the SDK leaves it unchanged.

In practice, this lets you normalize tool names across providers. For example, your agent can consistently refer to `list_directory`, while Spring configuration maps it to provider-specific names such as `list_files_in_folder`.

> [!TIP]
> Alias order matters. Put the preferred MCP tool name first, because the current `toolNameMapper` resolves the primary tool using the first configured alias.

If you are using the built-in remote ACP client, these Spring properties are also available:

```yaml
codeprompt:
  acp:
    remote:
      command: /usr/local/bin/docker
      args: agent serve acp src/test/resources/dataset/acp-client/ping-agent.yaml
      cwd: .
```

These map to `RemoteAgentCaller` constructor properties:

- `codeprompt.acp.remote.command` — executable to launch
- `codeprompt.acp.remote.args` — whitespace-split command arguments
- `codeprompt.acp.remote.cwd` — working directory for the launched process

### `LLMToToolEdge`

Reusable LangGraph edge helper used to route model output toward tool/MCP execution paths.

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 21 | Primary SDK implementation |
| **Kotlin** | 2.2.20 | ACP client interoperability |
| **Spring Boot** | 3.4.2 | Dependency injection and bootstrapping |
| **JetBrains ACP SDK** (`com.agentclientprotocol:acp`) | 0.15.3 | Agent Client Protocol support |
| **LangChain4j** | 1.11.0 | LLM abstraction and integrations |
| **LangGraph4j** | 1.8.3 | Graph orchestration primitives |
| **CommonMark** | 0.27.1 | Markdown parsing/rendering |

## Useful Commands

Run commands from the module root:


### Build / Test

```bash
./gradlew clean build --stacktrace
./gradlew test --stacktrace
```

### JaCoCo Coverage

```bash
./gradlew test jacocoTestReport --stacktrace
```

### Dependency Verification (Checksums/Metadata)

Refresh metadata after dependency/plugin changes (lenient), then validate strict mode:

```bash
./gradlew --refresh-dependencies --dependency-verification lenient --write-verification-metadata sha256 help
./gradlew --dependency-verification strict build -x test --stacktrace
```

If strict mode fails after updates, regenerate metadata across CI-like classpaths and retry strict mode:

```bash
./gradlew --refresh-dependencies --dependency-verification lenient --write-verification-metadata sha256 \
  build test jacocoTestReport check dependencyCheckAnalyze sonar \
  -Dsonar.qualitygate.wait=false \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=dummy \
  -Dsonar.organization=dummy \
  -Dsonar.projectKey=dummy
./gradlew --dependency-verification strict test --stacktrace
```

### SonarQube / SonarCloud Analysis

Set coordinates and token:

```bash
export SONAR_HOST_URL="https://sonarcloud.io"
export SONAR_TOKEN="<token>"
export SONAR_ORGANIZATION="<organization-key>"
export SONAR_PROJECT_KEY="<project-key>"
```

Run analysis and wait for quality gate:

```bash
./gradlew sonar -Dsonar.qualitygate.wait=true --stacktrace
```

### Dependency Vulnerability Scan

```bash
./gradlew dependencyCheckAnalyze --stacktrace
```

### Publish

```bash
./gradlew publishToMavenLocal --stacktrace
./gradlew clean build publishToMavenLocal --stacktrace
```

### Build Release Artifacts

```bash
./gradlew jar sourcesJar javadocJar --stacktrace
```

### Use a Local Bridge Snapshot

Consume a specific local bridge version while iterating across modules:

```bash
./gradlew test -PbridgeVersion=1.0-SNAPSHOT --stacktrace
```

Or with environment variable:

```bash
export BRIDGE_VERSION="1.0-SNAPSHOT"
./gradlew test --stacktrace
```

## CI-like Local Validation

```bash
./gradlew --dependency-verification strict clean build test jacocoTestReport --stacktrace
./gradlew --dependency-verification strict dependencyCheckAnalyze --stacktrace
./gradlew --dependency-verification strict sonar -Dsonar.qualitygate.wait=true --stacktrace
```

## Troubleshooting

- `DependencyVerificationException`: refresh metadata in lenient mode, then rerun strict mode.
- `Task 'dependencyCheckAnalyze' not found`: ensure OWASP dependency-check plugin is applied.
- Sonar project errors: verify `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` match your SonarCloud settings.

## Notes

The SDK is designed to be consumed as a library module; use it from your own Spring Boot agent application and wire your graph/prompt logic around the provided parser and ACP client abstractions.
