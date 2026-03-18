package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.List;
import java.util.Objects;

public record McpHeader(List<String> value) implements MarkdownHeader {

	public static final String MCP = "mcp";
	public static final String TOOLS_ALIAS = "tools";

	public McpHeader {
		Objects.requireNonNull(value, "value must not be null");
		value = value.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
	}

	@Override
	public String key() {
		return MCP;
	}
}

