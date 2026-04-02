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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillResolverTest {

    private SkillResolver skillResolver;

    private static CodepromptConfiguration configuredTestSettings() {
        CodepromptConfiguration config = new CodepromptConfiguration();
        config.getAgent().setSkillFolders(List.of("classpath:dataset/markdown/skills/"));
        return config;
    }

    private static SkillParser skillParser() {
        return skillFile -> null;
    }

    private static ResourcePatternResolver failingResourcePatternResolver() {
        return new ResourcePatternResolver() {
            @Override
            @NonNull
            public Resource getResource(@NonNull String location) {
                return new ByteArrayResource(new byte[0]);
            }

            @Override
            @NonNull
            public Resource[] getResources(@NonNull String locationPattern) throws IOException {
                throw new IOException("resolver failure for pattern: " + locationPattern);
            }

            @Override
            @NonNull
            public ClassLoader getClassLoader() {
                return SkillResolverTest.class.getClassLoader();
            }
        };
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

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
        assertThat(skills.getFirst().getName()).isEqualTo("implements_features_file");
    }

    @Test
    void shouldReturnEmptyListWhenNoSkillNamesGiven() {
        List<Skill> skills = skillResolver.resolveSkills(List.of());

        assertThat(skills).isEmpty();
    }

    @Test
    void shouldResolveToLocationPrefixForAllSupportedInputForms() {
        assertThat(invokeToLocationPrefix("classpath*:/dataset/markdown/skills/")).isEqualTo("classpath*:/dataset/markdown/skills");
        assertThat(invokeToLocationPrefix("classpath:/dataset/markdown/skills/")).isEqualTo("classpath*:/dataset/markdown/skills");
        assertThat(invokeToLocationPrefix("file:/tmp/skills/")).isEqualTo("file:/tmp/skills");
        assertThat(invokeToLocationPrefix(Paths.get("src/test/resources/dataset/markdown/skills/").toString()))
                .isEqualTo(trimTrailingSlashes(Paths.get("src/test/resources/dataset/markdown/skills/").toAbsolutePath().normalize().toUri().toString()));
    }

    @Test
    void shouldWrapIOExceptionWhenResolvingSkillsFromConfiguredBaseFolder() {
        SkillResolverImpl resolver = new SkillResolverImpl(
                configuredTestSettings(),
                skillParser(),
                failingResourcePatternResolver()
        );

        IllegalStateException exception = invokeResolveSkillFromBaseFolder(resolver);

        assertThat(exception)
                .hasMessageContaining("Error resolving skills with pattern")
                .hasCauseInstanceOf(IOException.class);
        assertThat(exception.getCause()).hasMessageContaining("resolver failure");
    }

    @Test
    void shouldThrowWhenSkillFolderDoesNotContainNamedSkill() {
        assertThatThrownBy(() -> skillResolver.resolveSkills(List.of("nonexistent-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent-skill");
    }

    private String invokeToLocationPrefix(String baseFolder) {
        try {
            Method method = SkillResolverImpl.class.getDeclaredMethod("toLocationPrefix", String.class);
            method.setAccessible(true);
            return (String) method.invoke(skillResolver, baseFolder);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private IllegalStateException invokeResolveSkillFromBaseFolder(SkillResolverImpl resolver) {
        try {
            Method method = SkillResolverImpl.class.getDeclaredMethod("resolveSkillFromBaseFolder", String.class, String.class);
            method.setAccessible(true);
            method.invoke(resolver, "classpath:dataset/markdown/skills/", "implements_features_file");
            throw new AssertionError("Expected resolveSkillFromBaseFolder to throw an IllegalStateException");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException illegalStateException) {
                return illegalStateException;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
