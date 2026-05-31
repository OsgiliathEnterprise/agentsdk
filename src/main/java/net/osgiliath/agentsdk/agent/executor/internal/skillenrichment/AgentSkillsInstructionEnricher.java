package net.osgiliath.agentsdk.agent.executor.internal.skillenrichment;

import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentsdk.agent.parser.Agent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
public class AgentSkillsInstructionEnricher {

    private final AgentSkillsInstructionInterpreter interpreter;
    private final AgentSkillsPayloadEnricher payloadEnricher;

    public AgentSkillsInstructionEnricher(AgentSkillsInstructionInterpreter interpreter,
                                          AgentSkillsPayloadEnricher payloadEnricher) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
        this.payloadEnricher = Objects.requireNonNull(payloadEnricher, "payloadEnricher must not be null");
    }

    public Optional<EagerSkillContextRequest> extractRequest(AiMessage aiMessage) {
        return interpreter.extractRequest(aiMessage);
    }

    public String requestSignature(EagerSkillContextRequest request) {
        return interpreter.requestSignature(request);
    }

    public String protocolInstruction(Agent agent) {
        return interpreter.protocolInstruction(agent);
    }

    public String renderPayload(Agent agent, EagerSkillContextRequest request) {
        return payloadEnricher.renderPayload(agent, request);
    }

    public record EagerSkillContextRequest(List<EagerSkillContentQuery> queries) {

        public EagerSkillContextRequest {
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }

    public record EagerSkillContentQuery(String skill,
                                         List<String> templates,
                                         List<String> assets,
                                         List<String> contentSections,
                                         List<String> assertDomains,
                                         List<String> assertCheckIds,
                                         boolean commands) {

        public EagerSkillContentQuery {
            skill = skill == null ? "" : skill.trim();
            templates = templates == null ? List.of() : List.copyOf(templates);
            assets = assets == null ? List.of() : List.copyOf(assets);
            contentSections = contentSections == null ? List.of() : List.copyOf(contentSections);
            assertDomains = assertDomains == null ? List.of() : List.copyOf(assertDomains);
            assertCheckIds = assertCheckIds == null ? List.of() : List.copyOf(assertCheckIds);
        }

        static EagerSkillContentQuery fromLegacy(String skill, List<String> include) {
            List<String> normalized = include == null ? List.of() : include.stream()
                    .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                    .toList();
            boolean includeAll = normalized.contains("all");
            boolean commands = includeAll || normalized.contains("commands");
            List<String> templates = includeAll || normalized.contains("templates") ? List.of("*") : List.of();
            List<String> assets = includeAll || normalized.contains("assets") ? List.of("*") : List.of();
            List<String> sections = includeAll || normalized.contains("content") ? List.of("*") : List.of();
            List<String> domains = includeAll || normalized.contains("asserts") ? List.of("*") : List.of();
            return new EagerSkillContentQuery(skill, templates, assets, sections, domains, List.of(), commands);
        }
    }
}
