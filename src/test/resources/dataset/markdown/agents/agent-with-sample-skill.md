---
name: "Skill Loaded Agent"
description: "Verifies skill resolution and resource loading"
argument-hint: "[input for the agent]"
tools:
  - read
  - search
model:
  - mini
user-invokable: true
disable-model-invocation: false
skills:
  - "sample-skill"
---

# Skill Loaded Agent

This agent is used to verify that parsed skills are loaded into the agent model and rendered into the system prompt.

