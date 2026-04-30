package net.osgiliath.agentsdk.skills.resolver;

import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.configuration.MarkdownConfiguration;
import net.osgiliath.agentsdk.skills.parser.Skill;
import net.osgiliath.agentsdk.skills.parser.SkillParser;
import net.osgiliath.agentsdk.skills.parser.SkillParserImpl;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParser;
import net.osgiliath.agentsdk.utils.markdown.MarkdownParserImpl;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolverImpl;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillResolverTest {

    private SkillResolver skillResolver;
    private ResourceLocationResolver resourceLocationResolver;

    private static CodepromptConfiguration configuredTestSettings() {
        CodepromptConfiguration config = new CodepromptConfiguration();
        config.getAgent().setSkillFolders(List.of("classpath:dataset/markdown/skills/"));
        return config;
    }

    private static SkillParser skillParser() {
        return new SkillParser() {
            @Override
            public Skill getSkill(org.springframework.core.io.Resource resource) {
                return null;
            }
        };
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
        resourceLocationResolver = new ResourceLocationResolverImpl(new PathMatchingResourcePatternResolver());
        SkillParser skillParser = new SkillParserImpl(markdownParser, commonmarkParser, resourceLocationResolver,
                new net.osgiliath.agentsdk.skills.assertions.SkillAssertionSetParser(resourceLocationResolver, new com.fasterxml.jackson.databind.ObjectMapper()));

        CodepromptConfiguration config = new CodepromptConfiguration();
        config.getAgent().setSkillFolders(List.of("classpath:dataset/markdown/skills/"));

        skillResolver = new SkillResolverImpl(config, skillParser, resourceLocationResolver);
    }

    @Test
    void shouldResolveSkillByFolderName() {
        List<Skill> skills = skillResolver.resolveSkills(List.of("implements_features_file"));

        assertThat(skills).hasSize(1);
        assertThat(skills.getFirst().getName()).isEqualTo("implements_features_file");
    }

    @Test
    void shouldThrowWhenNoSkillNamesGiven() {
        assertThatThrownBy(() -> skillResolver.resolveSkills(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("skillNames must not be empty");
    }

    @Test
    void shouldResolveSearchPrefixForAllSupportedInputForms() {
        assertThat(resourceLocationResolver.toSearchPrefix("classpath*:/dataset/markdown/skills/"))
                .isEqualTo("classpath*:/dataset/markdown/skills");
        assertThat(resourceLocationResolver.toSearchPrefix("classpath:/dataset/markdown/skills/"))
                .isEqualTo("classpath*:/dataset/markdown/skills");
        assertThat(resourceLocationResolver.toSearchPrefix("file:/tmp/skills/"))
                .isEqualTo("file:/tmp/skills");

        String expectedPathPrefix = trimTrailingSlashes(Paths.get("src/test/resources/dataset/markdown/skills/")
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString());
        assertThat(resourceLocationResolver.toSearchPrefix(Paths.get("src/test/resources/dataset/markdown/skills/").toString()))
                .isEqualTo(expectedPathPrefix);
    }

    @Test
    void shouldWrapIOExceptionWhenResolvingSkillsFromConfiguredBaseFolder() {
        SkillResolverImpl resolver = new SkillResolverImpl(
                configuredTestSettings(),
                skillParser(),
                new ResourceLocationResolverImpl(failingResourcePatternResolver())
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

    private IllegalStateException invokeResolveSkillFromBaseFolder(SkillResolverImpl resolver) {
        try {
            java.lang.reflect.Method method = SkillResolverImpl.class.getDeclaredMethod("resolveSkillFromBaseFolder", String.class, String.class);
            method.setAccessible(true);
            method.invoke(resolver, "classpath:dataset/markdown/skills/", "implements_features_file");
            throw new AssertionError("Expected resolveSkillFromBaseFolder to throw an IllegalStateException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException illegalStateException) {
                return illegalStateException;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
