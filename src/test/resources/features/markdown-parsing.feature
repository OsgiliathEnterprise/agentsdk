Feature: Markdown Parsing
  As a developer
  I want to parse markdown files correctly
  So that I can extract and process their content

  Scenario: SC1 - List markdown files in a folder
    Given a folder "test-markdown" containing markdown files
      | agent1.md        |
      | agent2.md        |
    When I list all markdown files in the folder
    Then I should find 2 markdown files
    And the files should include "agent1.md"
    And the files should include "agent2.md"

  Scenario: SC2 - Parse headers of markdown files
    Given a markdown file "example.md" with the following content:
      """
      ---
      name: docx
      description: "Comprehensive document creation, editing, and analysis with support for tracked changes, comments, formatting preservation, and text extraction. When Claude needs to work with professional documents (.docx files) for: (1) Creating new documents, (2) Modifying or editing content, (3) Working with tracked changes, (4) Adding comments, or any other document tasks"
      license: Proprietary. LICENSE.txt has complete terms
      ---
      # Main Title
      This is the introduction.

      ## Section 1
      Content of section 1.

      ### Subsection 1.1
      Detailed content.

      ## Section 2
      Content of section 2.
      """
    When I parse the headers of the markdown file
    Then I should extract the following headers:
      | text           |
      | name           |
      | description    |
      | license        |

  Scenario: SC3 - Parse the Main section of a markdown file
    Given a markdown file "document.md" with the following content:
      """
      # Main Title

      ## Main
      This is the main section content.
      It contains multiple paragraphs.

      And additional information.

      ## Other Section
      This should not be included.
      """
    When I parse the "Main" section of the markdown file
    Then the Main section content should be:
      """
      This is the main section content.
      It contains multiple paragraphs.

      And additional information.
      """
    And the Main section should not contain "Other Section"

  Scenario: SC4 - Parse the Samples section of a markdown file
    Given a markdown file "api-docs.md" with the following content:
      """
      # API Documentation

      ## Overview
      This API provides various endpoints.

      ## Samples
      Here are some usage examples:

      ### Example 1: Basic Usage
      ```java
      String result = api.call("param");
      ```

      ### Example 2: Advanced Usage
      ```java
      Map<String, String> config = new HashMap<>();
      api.configure(config);
      ```

      ## Configuration
      Configuration details here.
      """
    When I parse the "Samples" section using the specific samples parser
    Then the Samples section should contain 2 examples
    And the Samples section should contain code block "String result = api.call(\"param\");"
    And the Samples section should contain code block "Map<String, String> config = new HashMap<>();"
    And the Samples section should not include "Configuration details"

  Scenario: SC5 - Follow and consolidate links in markdown
    Given a markdown file "index.md" with the following content:
      """
      # Index

      See [documentation](./docs.md) for details.
      Check [API reference](./api.md) for API info.
      Visit [external site](https://example.com) for more.
      """
    When I follow and consolidate all markdown links
    Then I should identify 3 links
    And the link "./docs.md" should be marked as internal
    And the link "./api.md" should be marked as internal
    And the link "https://example.com" should be marked as external
    And the consolidated content should include content from "docs.md"
    And the consolidated content should include content from "api.md"
    And external links should be preserved but not followed
    And the consolidated content should include Easter egg values from linked files

  Scenario: SC5 - Handle circular references when following links
    Given a markdown file "page1.md" with a link to "page2.md"
    And a markdown file "page2.md" with a link to "page1.md"
    When I follow and consolidate all markdown links
    Then the parser should detect circular references
    And each file should be processed only once
    And no infinite loop should occur
