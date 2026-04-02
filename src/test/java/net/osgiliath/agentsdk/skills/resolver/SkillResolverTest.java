package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.skills.parser.SkillParserImpl;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParserImpl;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillResolverTest {

    private SkillResolver skillResolver;

    @BeforeEach
    void setUp() {
        Parser commonmarkParser = new MarkdownConfiguration().markdownParser();
        MarkdownParser markdownParser = new MarkdownParserImpl(commonmarkParser);
        SkillParser skillParser = new SkillParserImpl(markdownParser, commonmarkParser);

        CodepromptConfiguration config = new CodepromptConfiguration();
        config.getAgent().setSkillFolders(List.of("classpath:dataset/markdown/skills/"));

        skillResolver = new SkillResolverImpl(config, skillParser, new PathMatchingResourcePatternResolver());
    }

    @Test
    void shouldResolveSkillByFolderName() {
        List<Skill> skills = skillResolver.resolveSkills(List.of("implements_features_file"));

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getName()).isEqualTo("implements_features_file");
    }

    @Test
    void shouldReturnEmptyListWhenNoSkillNamesGiven() {
        List<Skill> skills = skillResolver.resolveSkills(List.of());

        assertThat(skills).isEmpty();
    }

    @Test
    void shouldThrowWhenSkillFolderDoesNotContainNamedSkill() {
        assertThatThrownBy(() -> skillResolver.resolveSkills(List.of("nonexistent-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent-skill");
    }
}
