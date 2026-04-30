package net.osgiliath.agentsdk.skills.assertions;

import java.util.List;

/**
 * Evaluates a list of {@link SkillAssertionSet}s against a workspace directory.
 * Works with both filesystem and packaged resources via ResourceLocationResolver.
 */
public interface SkillAssertionEvaluator {

    /**
     * Evaluates all mechanical checks in the given assertion sets against the workspace path.
     * Rule-only checks (no paths/files/signals) are reported as
     * {@link SkillAssertionStatus#NOT_EVALUATED}.
     *
     * @param assertionSets non-null list of assertion sets (may be empty)
     * @param workspacePath workspace path (filesystem or resource-resolvable path)
     * @return aggregated evaluation result
     */
    SkillAssertionEvaluation evaluate(List<SkillAssertionSet> assertionSets, String workspacePath);
}

