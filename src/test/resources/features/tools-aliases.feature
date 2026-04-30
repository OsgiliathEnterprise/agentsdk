Feature: MCP Tool Aliases Resolution

  Background:
    Given a parsed agent from "dataset/markdown/tools-aliases/agent1-with-skill.md"
    And the MCP tool aliases configuration is loaded

  Scenario: SC1 - Alias values are used when a tool is declared in both the agent metadata and the aliases configuration
    Given the tool "list_directory" is declared in the agent tools list
    And "list_directory" is present in the aliases configuration
    When the alias resolver computes the MCP tool names for "list_directory"
    Then the resolved tool names should contain:
      | list_files_in_folder |
      | list_directory       |

  Scenario: SC2 - Original tool name is used when the tool is declared in the agent metadata but absent from the aliases configuration
    Given the tool "git_checkout" is declared in the agent tools list
    And "git_checkout" is not present in the aliases configuration
    When the alias resolver computes the MCP tool names for "git_checkout"
    Then the resolved tool names should contain:
      | git_checkout |

  Scenario: SC3 - First alias value is the primary tool name called when a tool is in both the agent metadata and the aliases configuration
    Given the tool "list_directory" is declared in the agent tools list
    And "list_directory" is present in the aliases configuration
    When the alias resolver computes the primary MCP tool name for "list_directory"
    Then the primary resolved tool name should be "list_files_in_folder"

  Scenario: SC4 - If the aliased tool is not available, take the next value
    Given the tool "directory_tree" is declared in the agent tools list
    And "directory_tree" is present in the aliases configuration
    When the alias resolver computes the MCP tool names for "directory_tree"
    Then the resolved tool names should contain:
      | list_directory_tree_in_folder |
      | directory_tree                |
    And the resolved tool names should have a fallback order for unavailable tools

