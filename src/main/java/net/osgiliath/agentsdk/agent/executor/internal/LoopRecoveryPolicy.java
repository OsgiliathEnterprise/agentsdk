package net.osgiliath.agentsdk.agent.executor.internal;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.llm.LLMS_KIND;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluation;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionEvaluator;
import net.osgiliath.agentsdk.skills.model.SkillsHeaders;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LoopRecoveryPolicy {

    private static final Logger log = LoggerFactory.getLogger(LoopRecoveryPolicy.class);
    private static final int MAX_MEMORY_RESETS = 5;
    private static final int MAX_BRIEFING_CHARS = 3200;
    private static final int MAX_SECTION_SNIPPETS = 5;
    private static final int MAX_SECTION_CONTENT_CHARS = 320;
    private static final int MAX_RESET_SUMMARY_PROMPT_CHARS = 4000;
    private static final int MAX_RESET_SUMMARY_TEXT_CHARS = 1200;

    private final ChatModel chatModel;

    public LoopRecoveryPolicy(@Qualifier("primaryChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public boolean canResetMemory(int memoryResetCount) {
        return memoryResetCount < MAX_MEMORY_RESETS;
    }

    public int maxTotalIterations(int maxIterations) {
        return maxIterations * (MAX_MEMORY_RESETS + 1);
    }

    public SkillAssertionEvaluation resolveRecoveryEvaluation(AgentToolLoopResult result,
                                                              AgentToolLoopRequest request,
                                                              SkillAssertionEvaluator assertionEvaluator) {
        return result.getAssertionEvaluation().orElseGet(() -> {
            if (request.assertionSets().isEmpty() || request.workspace().isBlank()) {
                return new SkillAssertionEvaluation(true, List.of());
            }
            return Optional.ofNullable(assertionEvaluator.evaluate(request.assertionSets(), request.workspace()))
                    .orElseGet(() -> new SkillAssertionEvaluation(true, List.of()));
        });
    }

    public String recoveryPrompt() {
        return "System recovery: You were caught in a repetitive loop or reached a stuck state. "
                + "Your conversation history has been cleared to break the cycle. "
                + "Mandatory next step: your very next response must call at least one concrete tool to make progress. "
                + "Do not reply with plain text only; identify the required tool action and execute it immediately. "
                + "Continue tool execution until failing assertions are fixed, then provide the completion response.";
    }

    public String buildAssertionRecoveryBriefing(AgentToolLoopRequest request,
                                                 SkillAssertionEvaluation evaluation) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        if (evaluation.passed()) {
            return "";
        }

        Agent agent = request.agent();
        String agentName = safe(agent.getName());
        String agentDescription = truncate(safe(agent.getDescription()), 360);
        String assertionFeedback = truncate(evaluation.formatFeedback(), 1400);

        String orderedToolPlan = toBulletList("- ", deduplicate(agent.getAllToolNames()), 16, "no declared tools");
        String skillOverview = summarizeSkills(agent.getSkillHeaders());
        String relevantSteps = summarizeRelevantSections(agent.getLevel1Content());
        String llmHint = summarizeLlmHint(agent);

        String briefing = "Reset recovery briefing (agent+skills scan):\n"
                + "Target agent: " + agentName + "\n"
                + "Agent description: " + agentDescription + "\n\n"
                + "Failing assertions to fix now:\n" + assertionFeedback + "\n\n"
                + "Preferred tool plan (execute in this order where applicable):\n" + orderedToolPlan + "\n\n"
                + "Relevant skill capabilities:\n" + skillOverview + "\n\n"
                + "Relevant execution steps from agent instructions:\n" + relevantSteps + "\n\n"
                + "Model capability hint: " + llmHint + "\n\n"
                + "Rule: keep calling tools and re-checking assertions until all required checks pass. "
                + "Do not finalize with plain text while any critical/major assertion remains failing.";
        return truncate(briefing, MAX_BRIEFING_CHARS);
    }

    public String buildResetRecoverySummaryInFreshSession(AgentToolLoopRequest request,
                                                          SkillAssertionEvaluation evaluation,
                                                          AgentToolLoopResult result,
                                                          String recoveryBriefing) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(recoveryBriefing, "recoveryBriefing must not be null");

        if (evaluation.passed()) {
            return "";
        }

        String summarySessionId = "assertion-reset-summary-" + UUID.randomUUID();
        String prompt = "You are in a fresh isolated recovery session. Session id: " + summarySessionId + ". "
                + "Interpret failing assertion text and summarize exactly what to do next. "
                + "Return at most 8 concise bullets with: "
                + "(1) failed assertions to fix first, "
                + "(2) concrete tools to call in order, "
                + "(3) required evidence proving assertions are fixed, "
                + "(4) completion condition for leaving the inner reset loop. "
                + "Do not output markdown code fences.\n\n"
                + "Loop name: " + request.loopName() + "\n"
                + "Workspace: " + truncate(request.workspace(), 300) + "\n"
                + "Internal reset reason: " + result.exitReason() + " | " + truncate(nullToEmpty(result.exitDetails()), 240) + "\n"
                + "Agent: " + request.agent().getName() + "\n"
                + "Skills: " + truncate(String.join(", ", request.agent().getSkillsName()), 700) + "\n"
                + "Declared tools: " + truncate(String.join(", ", request.agent().getAllToolNames()), 1000) + "\n\n"
                + "Assertion feedback:\n" + truncate(evaluation.formatFeedback(), 1600) + "\n\n"
                + "Scanned recovery context:\n" + truncate(recoveryBriefing, 1800);

        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(UserMessage.from(truncate(prompt, MAX_RESET_SUMMARY_PROMPT_CHARS))))
                    .build());
            String text = response.aiMessage() == null ? "" : nullToEmpty(response.aiMessage().text()).trim();
            if (text.isBlank()) {
                return "";
            }
            return truncate("Fresh recovery summary (" + summarySessionId + "):\n" + text,
                    MAX_RESET_SUMMARY_TEXT_CHARS);
        } catch (Exception ex) {
            log.debug("{} could not build fresh reset recovery summary: {}",
                    request.loopName(), ex.getMessage(), ex);
            return "";
        }
    }

    private String summarizeSkills(java.util.Collection<SkillsHeaders> skillHeaders) {
        if (skillHeaders == null || skillHeaders.isEmpty()) {
            return "- no skills attached";
        }
        List<String> lines = new ArrayList<>();
        for (SkillsHeaders skillHeader : skillHeaders) {
            String skillName = safe(skillHeader.name().value());
            String description = truncate(safe(skillHeader.description().value()), 180);
            String tools = joinOrFallback(skillHeader.mcp().value(), ", ", "none");
            String llms = joinOrFallback(skillHeader.llm().value().stream().map(LLMS_KIND::getName).toList(), ", ", "unspecified");
            lines.add("- " + skillName + ": " + description + " | tools=" + tools + " | llm=" + llms);
        }
        return truncate(String.join("\n", lines), 1200);
    }

    private String summarizeRelevantSections(List<MarkdownSection> topSections) {
        if (topSections == null || topSections.isEmpty()) {
            return "- no process sections found";
        }
        List<MarkdownSection> flat = new ArrayList<>();
        for (MarkdownSection section : topSections) {
            flattenSections(section, flat);
        }
        List<String> snippets = flat.stream()
                .filter(this::isRecoveryRelevantSection)
                .limit(MAX_SECTION_SNIPPETS)
                .map(section -> "- " + safe(section.getTitle()) + ": "
                        + truncate(compactWhitespace(safe(section.getContent())), MAX_SECTION_CONTENT_CHARS))
                .toList();
        if (snippets.isEmpty()) {
            return "- no directly relevant sections identified";
        }
        return String.join("\n", snippets);
    }

    private void flattenSections(MarkdownSection section, List<MarkdownSection> output) {
        if (section == null) {
            return;
        }
        output.add(section);
        List<MarkdownSection> subSections = section.getSubSections();
        if (subSections == null || subSections.isEmpty()) {
            return;
        }
        for (MarkdownSection subSection : subSections) {
            flattenSections(subSection, output);
        }
    }

    private boolean isRecoveryRelevantSection(MarkdownSection section) {
        String title = safe(section.getTitle()).toLowerCase(Locale.ROOT);
        if (title.isBlank()) {
            return false;
        }
        return title.contains("process")
                || title.contains("execution")
                || title.contains("flow")
                || title.contains("mode")
                || title.contains("guard")
                || title.contains("completion")
                || title.contains("mandatory")
                || title.contains("resync")
                || title.contains("apply")
                || title.contains("verify");
    }

    private String summarizeLlmHint(Agent agent) {
        List<LLMS_KIND> declared = Stream.concat(
                        agent.getLlms().stream(),
                        agent.getSkillHeaders().stream().flatMap(skill -> skill.llm().value().stream()))
                .distinct()
                .toList();
        Optional<LLMS_KIND> highest = declared.stream()
                .max(java.util.Comparator.comparingInt(Enum::ordinal));
        if (highest.isPresent()) {
            String all = joinOrFallback(declared.stream().map(LLMS_KIND::getName).toList(), ", ", highest.get().getName());
            return "prefer higher-capability reasoning within declared kinds (highest="
                    + highest.get().getName() + "; declared=" + all + ") when planning assertion fixes.";
        }
        return "no explicit llm kind declared; increase reasoning depth and prioritize deterministic tool execution.";
    }

    private List<String> deduplicate(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private String toBulletList(String prefix, List<String> values, int limit, String fallback) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        if (values == null || values.isEmpty()) {
            return prefix + fallback;
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .limit(limit)
                .map(v -> prefix + v)
                .collect(Collectors.joining("\n"));
    }

    private String joinOrFallback(List<String> values, String delimiter, String fallback) {
        Objects.requireNonNull(delimiter, "delimiter must not be null");
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        String joined = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(delimiter));
        return joined.isBlank() ? fallback : joined;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String compactWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxChars) {
        Objects.requireNonNull(value, "value must not be null");
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be greater than 0");
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }
}
