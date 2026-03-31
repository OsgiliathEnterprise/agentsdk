package net.osgiliath.agentsdk.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "codeprompt")
public class CodepromptConfiguration {

    private final AcpProperties acp = new AcpProperties();
    private final McpProperties mcp = new McpProperties();
    private final AgentProperties agent = new AgentProperties();
    private LlmKindModelProperties llms = new LlmKindModelProperties();

    public AcpProperties getAcp() {
        return acp;
    }

    public McpProperties getMcp() {
        return mcp;
    }

    public AgentProperties getAgent() {
        return agent;
    }

    public static class AgentProperties {
        private List<String> skillFolders = new ArrayList<>();
        private List<String> agentFolders = new ArrayList<>();

        public List<String> getSkillFolders() {
            return skillFolders;
        }

        public void setSkillFolders(List<String> skillFolders) {
            this.skillFolders = skillFolders == null ? new ArrayList<>() : new ArrayList<>(skillFolders);
        }

        public List<String> getAgentFolders() {
            return agentFolders;
        }

        public void setAgentFolders(List<String> agentFolders) {
            this.agentFolders = agentFolders == null ? new ArrayList<>() : new ArrayList<>(agentFolders);
        }
    }


    public LlmKindModelProperties getLlms() {
        return llms;
    }

    public void setLlms(LlmKindModelProperties llmProperties) {
        this.llms = llmProperties == null ? new LlmKindModelProperties() : llmProperties;
    }

    public static class AcpProperties {
        private final RemoteProperties remote = new RemoteProperties();

        public RemoteProperties getRemote() {
            return remote;
        }
    }

    public static class RemoteProperties {
        private String command = "java";
        private String args = "";
        private String cwd = ".";

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getArgs() {
            return args;
        }

        public void setArgs(String args) {
            this.args = args;
        }

        public String getCwd() {
            return cwd;
        }

        public void setCwd(String cwd) {
            this.cwd = cwd;
        }
    }

    public static class McpProperties {
        private final ToolsProperties tools = new ToolsProperties();

        public ToolsProperties getTools() {
            return tools;
        }
    }

    public static class ToolsProperties {
        private Map<String, List<String>> aliases = new HashMap<>();

        public Map<String, List<String>> getAliases() {
            return aliases;
        }

        public void setAliases(Map<String, List<String>> aliases) {
            this.aliases = aliases;
        }
    }
}
