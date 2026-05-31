package net.osgiliath.agentsdk.agent.executor;

@FunctionalInterface
public interface BlockingToolFailureStrategy {

    BlockingToolFailureStrategy NONE = text -> false;

    boolean isBlockingFailure(String toolResultText);
}

