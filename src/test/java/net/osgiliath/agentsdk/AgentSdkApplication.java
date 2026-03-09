package net.osgiliath.agentsdk;

import net.osgiliath.agentsdk.configuration.LangChain4jConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code Prompt Framework Application.
 *
 * Koog ACP Server Frontend + LangChain4j Agent Orchestrator
 */
@SpringBootApplication(scanBasePackages = "net.osgiliath.agentsdk")
public class AgentSdkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentSdkApplication.class, args);
    }
}




