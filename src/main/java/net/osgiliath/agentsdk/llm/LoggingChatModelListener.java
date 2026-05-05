package net.osgiliath.agentsdk.llm;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A {@link ChatModelListener} that logs every LLM request/response exchange at DEBUG level
 * and errors at WARN level to ease test-scenario troubleshooting.
 * <p>
 * Spring Boot's LangChain4j auto-configuration automatically discovers all {@link ChatModelListener}
 * beans and wires them into every chat model; registering this class as a bean is sufficient.
 */
public class LoggingChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatModelListener.class);

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        Objects.requireNonNull(requestContext, "requestContext must not be null");
        if (log.isDebugEnabled()) {
            log.debug(
                    "[LLM REQUEST] model={} messages={}",
                    requestContext.chatRequest().modelName(),
                    requestContext.chatRequest().messages()
            );
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Objects.requireNonNull(responseContext, "responseContext must not be null");
        if (log.isDebugEnabled()) {
            log.debug(
                    "[LLM RESPONSE] model={} finishReason={} tokenUsage={} message={}",
                    responseContext.chatResponse().modelName(),
                    responseContext.chatResponse().finishReason(),
                    responseContext.chatResponse().tokenUsage(),
                    responseContext.chatResponse().aiMessage()
            );
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Objects.requireNonNull(errorContext, "errorContext must not be null");
        log.warn(
                "[LLM ERROR] model={} messages={} error={}",
                errorContext.chatRequest().modelName(),
                errorContext.chatRequest().messages(),
                errorContext.error().getMessage(),
                errorContext.error()
        );
    }
}
