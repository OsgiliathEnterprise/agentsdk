package net.osgiliath.agentsdk.skills.acpclient;

import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.agentsdk.AgentSdkApplication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end ACP test that calls a real cagent process configured with ping-agent.yaml.
 */
@SpringBootTest(classes = AgentSdkApplication.class, properties = {
    "spring.main.web-application-type=none"
})
@Profile("!github")
@Tag("e2e")
class RemoteAgentCallerCagentE2EIT {

    @MockitoBean
    private CommandLineRunner commandLineRunner;

    @Autowired
    private RemoteAgentCaller remoteAgentCaller;

    @Value("${codeprompt.acp.remote.command:/usr/local/bin/docker agent serve acp src/test/resources/dataset/acp-client/ping-agent.yaml}")
    private String cagentCommand;

    private String pingPrompt = "Reply with exactly Ping.";

    @AfterEach
    void shutdownRemoteClient() {
        remoteAgentCaller.shutdown();
    }

    @Test
    void shouldCallPingAgentOverAcp() throws Exception {
        Assumptions.assumeTrue(isCommandAvailable(cagentCommand),
            () -> "Skipping E2E test because command is unavailable: " + cagentCommand);

        System.out.println("Running E2E test with command: " + cagentCommand);

        // Ensure ACP initialize handshake happens before session/prompt calls.
        AcpAgentSupportBridge.AgentInfoBridge agentInfo = remoteAgentCaller.getAgentInfo();
        System.out.println("Agent info: " + agentInfo.name() + " v" + agentInfo.version());

        AcpAgentSupportBridge.AcpSessionBridge session = remoteAgentCaller.createSession(
            "cagent-ping-e2e",
            ".",
            Collections.emptyMap()
        );

        String response = session.processPrompt(pingPrompt, Collections.emptyList())
            .get(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);

        assertThat(response)
            .as("The ping-agent should answer with Ping")
            .containsIgnoringCase("ping");
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("zsh", "-lc", "command -v " + command)
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor(5, TimeUnit.SECONDS) ? process.exitValue() : -1;
            if (exitCode != 0) {
                process.destroyForcibly();
            }
            return exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
