package net.osgiliath.agentsdk.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopExecutor;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopRequest;
import net.osgiliath.agentsdk.agent.executor.internal.AgentToolLoopResult;
import net.osgiliath.agentsdk.agent.executor.internal.StructuredOutcomeInterpreter;
import net.osgiliath.agentsdk.agent.parser.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultAgentExecutorTest {

    @Test
    void executeShouldDelegateToExecuteWithOutcomeRulesUsingDefaultRules() {
        AgentToolLoopExecutor toolLoopExecutor = mock(AgentToolLoopExecutor.class);
        DefaultAgentExecutor executor = new DefaultAgentExecutor(toolLoopExecutor, new StructuredOutcomeInterpreter(new ObjectMapper()));

        AgentToolLoopResult loopResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                AiMessage.from("{\"overall\":\"success\",\"reason\":\"project layout updated\"}"),
                List.of(UserMessage.from("u")));
        when(toolLoopExecutor.execute(any())).thenReturn(loopResult);

        AgentExecutionResult result = executor.execute(newRequest("memory"));

        assertThat(result.toolLoopResult()).isSameAs(loopResult);
        assertThat(result.executedPasses()).isEqualTo(1);
        assertThat(result.requireInterpretedOutcome().isSuccess()).isTrue();
        verify(toolLoopExecutor, times(1)).execute(any());
    }

    @Test
    void executeWithOutcomeRulesShouldClassifyOutcomeFromSingleDelegatedPass() {
        AgentToolLoopExecutor toolLoopExecutor = mock(AgentToolLoopExecutor.class);
        DefaultAgentExecutor executor = new DefaultAgentExecutor(toolLoopExecutor, new StructuredOutcomeInterpreter(new ObjectMapper()));
        OutcomeTextRules rules = new OutcomeTextRules("need-more-iteration:", "deferred:", Set.of("project layout updated"));

        AgentToolLoopResult loopResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                AiMessage.from("project layout updated"),
                List.of(UserMessage.from("u")));
        when(toolLoopExecutor.execute(any())).thenReturn(loopResult);

        AgentExecutionResult result = executor.executeWithOutcomeRules(newRequest("session"), rules);

        assertThat(result.executedPasses()).isEqualTo(1);
        assertThat(result.toolLoopResult()).isSameAs(loopResult);
        assertThat(result.requireInterpretedOutcome().isSuccess()).isTrue();
        verify(toolLoopExecutor, times(1)).execute(any());
    }

    @Test
    void executeWithOutcomeRulesShouldUseDefaultRulesWhenNullProvided() {
        AgentToolLoopExecutor toolLoopExecutor = mock(AgentToolLoopExecutor.class);
        DefaultAgentExecutor executor = new DefaultAgentExecutor(toolLoopExecutor, new StructuredOutcomeInterpreter(new ObjectMapper()));

        AgentToolLoopResult loopResult = new AgentToolLoopResult(
                AgentToolLoopResult.ExitReason.TERMINAL_MESSAGE,
                "",
                AiMessage.from("{\"overall\":\"success\",\"reason\":\"ok\"}"),
                List.of(UserMessage.from("u")));
        when(toolLoopExecutor.execute(any())).thenReturn(loopResult);

        AgentExecutionResult result = executor.executeWithOutcomeRules(newRequest("session"), null);

        assertThat(result.requireInterpretedOutcome().isSuccess()).isTrue();
        verify(toolLoopExecutor, times(1)).execute(any());
    }

    private AgentToolLoopRequest newRequest(String chatMemoryId) {
        Agent agent = mock(Agent.class);
        when(agent.getAssertionSets()).thenReturn(List.of());
        UserMessage userMessage = UserMessage.from("run");
        return AgentToolLoopRequest.of(
                agent,
                userMessage,
                chatMemoryId,
                InvocationParameters.from("cwd", "/tmp/ws"),
                ChatRequest.builder().messages(List.of(userMessage)).build(),
                "/tmp/ws",
                "test loop",
                3,
                1,
                8,
                BlockingToolFailureStrategy.NONE);
    }
}
