package net.osgiliath.agentsdk.common.parsing;

import net.osgiliath.agentsdk.utils.markdown.MarkdownHeader;

import java.util.List;
import java.util.Objects;

public record LlmHeader(List<String> value) implements MarkdownHeader {

	public static final String LLM = "llm";
	public static final String MODEL_ALIAS = "model";

	public LlmHeader {
		Objects.requireNonNull(value, "value must not be null");
		value = value.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
	}

	@Override
	public String key() {
		return LLM;
	}
}

