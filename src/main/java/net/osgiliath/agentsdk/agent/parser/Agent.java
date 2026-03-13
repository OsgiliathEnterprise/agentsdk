package net.osgiliath.agentsdk.agent.parser;

import net.osgiliath.agentsdk.utils.markdown.MarkdownSection;

import java.util.List;
import java.util.Objects;

public final class Agent {

    private final AgentHeaders headers;
    private final List<MarkdownSection> content;

    public Agent(AgentHeaders headers, List<MarkdownSection> content) {
        this.headers = Objects.requireNonNull(headers, "headers must not be null");
        this.content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
    }

    public AgentHeaders headers() {
        return headers;
    }

    public List<MarkdownSection> content() {
        return content;
    }
}
