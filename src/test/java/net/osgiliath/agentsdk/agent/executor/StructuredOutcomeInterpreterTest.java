package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutcomeInterpreterTest {

    private StructuredOutcomeInterpreter interpreter;
    private OutcomeTextRules rules;

    @BeforeEach
    void setUp() {
        interpreter = new StructuredOutcomeInterpreter(new ObjectMapper());
        rules = new OutcomeTextRules("need-more-iteration:", "deferred:", Set.of("project layout updated", "project audit passed"));
    }

    @Test
    void shouldClassifyStructuredJsonOutcomes() {
        AgentOutcome success = interpreter.classifyText("{\"overall\":\"pass\",\"reason\":\"all good\"}", rules);
        AgentOutcome deferred = interpreter.classifyText("{\"status\":\"pending\",\"reason\":\"later\"}", rules);
        AgentOutcome failed = interpreter.classifyText("noise {\"status\":\"failed\",\"reason\":\"retry me\"} tail", rules);

        assertThat(success.status()).isEqualTo(AgentOutcomeStatus.SUCCESS);
        assertThat(success.reason()).isEqualTo("all good");
        assertThat(deferred.status()).isEqualTo(AgentOutcomeStatus.DEFERRED);
        assertThat(deferred.reason()).isEqualTo("later");
        assertThat(failed.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(failed.reason()).isEqualTo("retry me");
    }

    @Test
    void shouldFallbackToMarkerAndSignalHeuristicsWhenTextIsNotStructuredJson() {
        AgentOutcome deferred = interpreter.classifyText("Operation DEFERRED: waiting for input", rules);
        AgentOutcome needMore = interpreter.classifyText("need-more-iteration: still running checks", rules);
        AgentOutcome success = interpreter.classifyText("The project audit passed and project layout updated", rules);
        AgentOutcome unknown = interpreter.classifyText("unrecognized status text", rules);

        assertThat(deferred.status()).isEqualTo(AgentOutcomeStatus.DEFERRED);
        assertThat(needMore.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(success.status()).isEqualTo(AgentOutcomeStatus.SUCCESS);
        assertThat(unknown.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
    }

    @Test
    void shouldClassifyTerminalResultByLoopExitReason() {
        AgentToolLoopResult errorResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ERROR,
                "boom",
                null,
                List.of());
        AgentToolLoopResult limitResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.ITERATION_LIMIT,
                "limit",
                null,
                List.of());
        AgentToolLoopResult repeatResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.REPEAT_GUARD,
                "repeat guard",
                null,
                List.of());

        assertThat(interpreter.classifyTerminalResult(errorResult, rules).status()).isEqualTo(AgentOutcomeStatus.DEFERRED);
        assertThat(interpreter.classifyTerminalResult(limitResult, rules).status()).isEqualTo(AgentOutcomeStatus.DEFERRED);
        assertThat(interpreter.classifyTerminalResult(repeatResult, rules).status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
    }

    @Test
    void shouldUseLastToolResultWhenTerminalTextIsBlank() {
        ChatMessage toolResultMessage = ToolExecutionResultMessage.builder()
                .id("tool-1")
                .toolName("audit")
                .text("project audit passed")
                .isError(false)
                .build();
        AgentToolLoopResult terminal = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                AiMessage.from(""),
                List.of(toolResultMessage));

        AgentOutcome outcome = interpreter.classifyTerminalResult(terminal, rules);

        assertThat(outcome.status()).isEqualTo(AgentOutcomeStatus.SUCCESS);
        assertThat(outcome.reason()).isEqualTo("project audit passed");
    }

    @Test
    void shouldAskForMoreIterationWhenTerminalTextAndToolResultAreBothBlank() {
        AgentToolLoopResult terminal = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                AiMessage.from(""),
                List.of());

        AgentOutcome outcome = interpreter.classifyTerminalResult(terminal, rules);

        assertThat(outcome.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(outcome.reason()).isEqualTo("empty terminal model response without tool result");
    }

    @Test
    void shouldFallbackToNeedMoreIterationForUnknownOrNonObjectStructuredPayload() {
        AgentOutcome unknownOverall = interpreter.classifyText("{\"overall\":\"maybe\",\"reason\":\"unclear\"}", rules);
        AgentOutcome arrayPayload = interpreter.classifyText("[1,2,3]", rules);

        assertThat(unknownOverall.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(arrayPayload.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
    }

    @Test
    void shouldHandleNullTerminalMessageWhenExitIsTerminal() {
        AgentToolLoopResult terminal = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                null,
                List.of());

        AgentOutcome outcome = interpreter.classifyTerminalResult(terminal, rules);

        assertThat(outcome.status()).isEqualTo(AgentOutcomeStatus.NEED_MORE_ITERATION);
        assertThat(outcome.reason()).isEqualTo("empty terminal model response without tool result");
    }
}


