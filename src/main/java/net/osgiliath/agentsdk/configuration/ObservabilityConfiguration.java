package net.osgiliath.agentsdk.configuration;

import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
