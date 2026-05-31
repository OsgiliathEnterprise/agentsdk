package net.osgiliath.agentsdk.agent.executor.internal.skillenrichment;

import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertion;
import net.osgiliath.agentsdk.skills.assertions.SkillAssertionCheck;
import net.osgiliath.agentsdk.skills.model.Skill;
import net.osgiliath.agentsdk.skills.model.SkillAsset;
import net.osgiliath.agentsdk.skills.model.SkillTemplate;
import net.osgiliath.agentsdk.skills.resolver.SkillResolver;
import net.osgiliath.agentsdk.skills.resolver.query.SkillAsserts;
import net.osgiliath.agentsdk.skills.resolver.query.SkillQuery;
import net.osgiliath.agentsdk.skills.resolver.query.SkillQueryBuilder;
import net.osgiliath.agentsdk.skills.resolver.query.SkillQueryRequest;
import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
public class AgentSkillsPayloadEnricher {

    private static final int MAX_SECTION_CHARS = 2800;
    private static final int MAX_RESOURCE_CHARS = 1600;
    private static final int MAX_RESPONSE_CHARS = 22000;

    private final SkillResolver skillResolver;
    private final SkillQueryBuilder skillQueryBuilder;

    public AgentSkillsPayloadEnricher(SkillResolver skillResolver,
                                      SkillQueryBuilder skillQueryBuilder) {
        this.skillResolver = Objects.requireNonNull(skillResolver, "skillResolver must not be null");
        this.skillQueryBuilder = Objects.requireNonNull(skillQueryBuilder, "skillQueryBuilder must not be null");
    }

    public String renderPayload(Agent agent, AgentSkillsInstructionEnricher.EagerSkillContextRequest request) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.queries().isEmpty()) {
            return "Eager skill context request is empty. Provide at least one query with a skill name.";
        }

        List<SkillQueryRequest> queryRequests = request.queries().stream()
                .map(query -> new SkillQueryRequest(
                        query.skill(),
                        query.templates(),
                        query.assets(),
                        query.contentSections(),
                        query.assertDomains(),
                        query.assertCheckIds(),
                        query.commands()))
                .toList();
        List<SkillQuery> queries = skillQueryBuilder.build(agent, queryRequests);

        StringBuilder builder = new StringBuilder();
        builder.append("Eager skill context payload (precise query mode).\\n");

        for (SkillQuery query : queries) {
            if (builder.length() >= MAX_RESPONSE_CHARS) {
                break;
            }
            appendSingleQueryPayload(agent, builder, query);
        }

        if (builder.length() > MAX_RESPONSE_CHARS) {
            return builder.substring(0, MAX_RESPONSE_CHARS) + "\\n...truncated...";
        }
        return builder.toString().trim();
    }

    private void appendSingleQueryPayload(Agent agent, StringBuilder builder, SkillQuery query) {
        String requestedSkill = query.skill();
        if (requestedSkill.isBlank()) {
            builder.append("- skipped query with blank skill name\\n");
            return;
        }

        if (!agent.getSkillsName().isEmpty() && !agent.getSkillsName().contains(requestedSkill)) {
            builder.append("- skill '").append(requestedSkill)
                    .append("' is not linked by this agent and was skipped\\n");
            return;
        }

        List<Skill> resolvedSkills;
        try {
            resolvedSkills = skillResolver.resolveSkills(List.of(requestedSkill));
        } catch (Exception exception) {
            builder.append("## Skill: ").append(requestedSkill).append("\\n")
                    .append("error: could not resolve skill: ").append(exception.getMessage()).append("\\n\\n");
            return;
        }

        if (resolvedSkills.isEmpty()) {
            builder.append("## Skill: ").append(requestedSkill).append("\\n")
                    .append("error: skill resolution returned no result\\n\\n");
            return;
        }

        Skill skill = resolvedSkills.getFirst();
        builder.append("## Skill: ").append(skill.getName()).append("\\n")
                .append("Description: ").append(skill.getDescription()).append("\\n");

        appendRequestedContentSections(builder, skill.getLevel1Content(), query.contentSections());
        appendRequestedTemplates(builder, skill.getTemplates(), query.templates());
        appendRequestedAssets(builder, skill.getAssets(), query.assets());
        SkillAsserts asserts = query.asserts();
        appendRequestedAsserts(builder, skill.getAssertionSets(), asserts.domains(), asserts.checkIds());
        if (query.commands()) {
            appendCommands(builder, skill);
        }
        builder.append("\\n");
    }

    private void appendRequestedContentSections(StringBuilder builder,
                                                List<MarkdownSection> sections,
                                                List<String> requestedSectionTitles) {
        if (sections.isEmpty() || requestedSectionTitles.isEmpty()) {
            return;
        }
        builder.append("### Requested content sections\\n");
        for (String requestedTitle : requestedSectionTitles) {
            Optional<MarkdownSection> matched = sections.stream()
                    .filter(section -> normalize(section.getTitle()).equals(normalize(requestedTitle)))
                    .findFirst();
            if (matched.isPresent()) {
                String content = matched.get().getContent() == null ? "" : matched.get().getContent().trim();
                builder.append("- ").append(requestedTitle).append(": ")
                        .append(truncate(content, MAX_SECTION_CHARS))
                        .append("\\n");
            } else {
                builder.append("- ").append(requestedTitle).append(": not found\\n");
            }
        }
    }

    private void appendRequestedTemplates(StringBuilder builder,
                                          List<SkillTemplate> templates,
                                          List<String> requestedTemplates) {
        if (requestedTemplates.isEmpty()) {
            return;
        }
        builder.append("### Requested templates\\n");
        for (String requestedTemplate : requestedTemplates) {
            Optional<SkillTemplate> matched = templates.stream()
                    .filter(template -> matchesRequestedPath(requestedTemplate, template.uri()))
                    .findFirst();
            if (matched.isPresent()) {
                builder.append("#### ").append(matched.get().uri()).append("\\n")
                        .append("```\\n")
                        .append(truncate(matched.get().content(), MAX_RESOURCE_CHARS))
                        .append("\\n```\\n");
            } else {
                builder.append("- ").append(requestedTemplate).append(": not found\\n");
            }
        }
    }

    private void appendRequestedAssets(StringBuilder builder,
                                       List<SkillAsset> assets,
                                       List<String> requestedAssets) {
        if (requestedAssets.isEmpty()) {
            return;
        }
        builder.append("### Requested assets\\n");
        for (String requestedAsset : requestedAssets) {
            Optional<SkillAsset> matched = assets.stream()
                    .filter(asset -> matchesRequestedPath(requestedAsset, asset.uri()))
                    .findFirst();
            if (matched.isPresent()) {
                builder.append("#### ").append(matched.get().uri()).append("\\n")
                        .append("```\\n")
                        .append(truncate(matched.get().content(), MAX_RESOURCE_CHARS))
                        .append("\\n```\\n");
            } else {
                builder.append("- ").append(requestedAsset).append(": not found\\n");
            }
        }
    }

    private void appendRequestedAsserts(StringBuilder builder,
                                        List<SkillAssertion> assertionSets,
                                        List<String> requestedDomains,
                                        List<String> requestedCheckIds) {
        if (requestedDomains.isEmpty() && requestedCheckIds.isEmpty()) {
            return;
        }
        builder.append("### Requested asserts\\n");
        boolean appendedAny = false;
        for (SkillAssertion set : assertionSets) {
            boolean domainSelected = requestedDomains.stream().anyMatch(domain -> normalize(domain).equals(normalize(set.domain())));
            List<SkillAssertionCheck> matchingChecks = set.checks().stream()
                    .filter(check -> requestedCheckIds.stream().anyMatch(id -> normalize(id).equals(normalize(check.id()))))
                    .toList();
            if (!domainSelected && matchingChecks.isEmpty()) {
                continue;
            }
            appendedAny = true;
            builder.append("- domain=").append(set.domain())
                    .append(", owner=").append(set.owner())
                    .append(", version=").append(set.version())
                    .append("\\n");
            List<SkillAssertionCheck> checksToAppend = domainSelected ? set.checks() : matchingChecks;
            for (SkillAssertionCheck check : checksToAppend) {
                builder.append("  - [").append(check.severity())
                        .append("][").append(check.id()).append("] ")
                        .append(check.title()).append("\\n");
                if (!check.requiredPaths().isEmpty()) {
                    builder.append("    required_paths=").append(check.requiredPaths()).append("\\n");
                }
                if (!check.requiredFiles().isEmpty()) {
                    builder.append("    required_files=").append(check.requiredFiles()).append("\\n");
                }
                if (!check.signals().isEmpty()) {
                    builder.append("    signals=").append(check.signals()).append("\\n");
                }
                if (!check.rule().isBlank()) {
                    builder.append("    rule=").append(truncate(check.rule(), 500)).append("\\n");
                }
            }
        }
        if (!appendedAny) {
            builder.append("- no matching assert domain/check id found\\n");
        }
    }

    private void appendCommands(StringBuilder builder, Skill skill) {
        if (skill.getCommands().isEmpty()) {
            builder.append("### Requested commands\\n- none\\n");
            return;
        }
        builder.append("### Requested commands\\n");
        for (var command : skill.getCommands()) {
            builder.append("- ").append(command.commandLine()).append("\\n");
        }
    }

    private boolean matchesRequestedPath(String requested, String candidate) {
        if ("*".equals(requested)) {
            return true;
        }
        return normalize(requested).equals(normalize(candidate));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

