Scenario: SC1 - skill headers should be parsed name, description, dependency, mcp servers, llms
  Given a skill file at "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md"
  When the skill parser reads the front matter header
  Then the parsed headers should contain:
    | key          |
    | name         |
    | description  |
    | dependencies |
    | mcp          |
    | llm          |

Scenario: SC2 - markdown links should be followed and parsed
  Given a parsed skill from "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md"
  When the parser resolves markdown links in the skill content
  Then linked markdown files should be loaded and parsed
  And the following linked markdown files should be followed:
    | uri                    |
    | examples/faq-answers.md |
    | agents/grader.md        |

Scenario: SC3 - assets uris should be stored
  Given a parsed skill from "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md"
  When the parser resolves non-markdown links
  Then asset URIs should be registered in the result
  And the following asset URI should be present:
    | uri                    |
    | assets/eval_review.html |

Scenario: SC4 - reference should be parsed
  Given a skill dataset root at "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill"
  When the parser reads the "reference" folder
  Then reference markdown documents should be parsed
  And "reference/evaluation.md" should be present in parsed references

Scenario: SC5 - scripts commands should be registered from skill instruction
  Given a parsed skill from "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill/SKILL.md"
  When command blocks are extracted from skill instructions
  Then script commands should be registered
  And at least one command should start with:
    | executable |
    | python     |
    | ./gradlew  |
    | pdftoppm   |

Scenario: SC6 - templates uris should be registered
  Given a skill dataset root at "agent-sdk/src/test/resources/dataset/markdown/skills/sample-skill"
  When template resources are scanned
  Then template URIs should be registered
  And "templates/generator_template.js" should be present in the template registry

Scenario: SC7 - results should be composed in different section
  Given a fully parsed skill with headers, body, links, assets, references, scripts, and templates
  When the parser composes the output model
  Then the result should be split into distinct sections:
    | section    |
    | headers    |
    | links      |
    | assets     |
    | references |
    | scripts    |
    | templates  |
    | content    |

Scenario: SC8 - aggregate document should be created
  Given a fully parsed and composed skill result
  When the aggregate document builder runs
  Then a non-empty aggregate document should be created
  And the aggregate document should include headers and composed content sections
  And duplicate linked content should not be duplicated in the aggregate output

