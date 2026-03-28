package net.osgiliath.agentsdk.llm;

public enum LLMS_KIND {
    THINKING("thinking"), // A simple LLM that does not support tool calls or function calls.
    NANO("nano"), // A small LLM that supports tool calls and function calls, but is not powerful enough to do complex reasoning on its own.
    MINI("mini"), // A medium LLM that supports tool calls and function calls, and is powerful enough to do some reasoning on its own.
    MEDIUM("medium"), // A large LLM that supports tool calls and function calls, and is powerful enough to do complex reasoning on its own.
    BIG("big"), // A very large LLM that supports tool calls and function calls, and is powerful enough to do very complex reasoning on its own.
    SUPER("super"), // A super large LLM that supports tool calls and function calls, and is powerful enough to do extremely complex reasoning on its own. Usually a remote API. like GPT-5 or Gemini.
    MAXI("maxi"); // A hypothetical future LLM that is even more powerful than SUPER, and can do things that are currently unimaginable. usually Claude
    private final String name;

    LLMS_KIND(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String chatBeanName() {
        return name + "ChatModel";
    }

    public String streamingBeanName() {
        return name + "StreamingChatModel";
    }
}
