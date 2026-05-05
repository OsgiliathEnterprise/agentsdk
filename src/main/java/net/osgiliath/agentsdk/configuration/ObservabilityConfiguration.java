package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import net.osgiliath.agentsdk.llm.LoggingChatModelListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers observability and diagnostic listeners that are automatically picked up
 * by LangChain4j Spring Boot auto-configuration and wired into every chat model.
 */
@Configuration
public class ObservabilityConfiguration {

    @Bean
    public MicrometerMetricsChatModelListener metricsChatModelListener(MeterRegistry meterRegistry) {
        return new MicrometerMetricsChatModelListener(meterRegistry);
    }

    @Bean
    public ObservationChatModelListener observationChatModelListener(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        return new ObservationChatModelListener(observationRegistry, meterRegistry);
    }

    /**
     * Logs every LLM request/response exchange at DEBUG level so that test scenarios can be
     * traced end-to-end without enabling a full HTTP wire log.
     */
    @Bean
    public LoggingChatModelListener loggingChatModelListener() {
        return new LoggingChatModelListener();
    }
}
