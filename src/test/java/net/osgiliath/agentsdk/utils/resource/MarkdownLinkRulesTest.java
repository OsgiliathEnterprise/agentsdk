package net.osgiliath.agentsdk.utils.resource;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownLinkRulesTest {

    @Test
    void normalizeForResolverRemovesFragmentsOnly() {
        assertThat(MarkdownLinkRules.normalizeForResolver(" ./docs/guide.md#intro "))
                .isEqualTo("./docs/guide.md");
    }

    @Test
    void normalizeForRelativeLookupAlsoRemovesRootAndCurrentDirPrefixes() {
        assertThat(MarkdownLinkRules.normalizeForRelativeLookup(" /./docs/guide.md#intro "))
                .isEqualTo("./docs/guide.md");
    }

    @Test
    void externalAndMarkdownChecksAreCaseInsensitive() {
        assertThat(MarkdownLinkRules.isExternal("HTTPS://example.com/doc.MD")).isTrue();
        assertThat(MarkdownLinkRules.isMarkdownResource("README.MD")).isTrue();
    }

    @Test
    void markdownResourceCanBeDetectedFromResolvedFilename() {
        Resource withoutExtensionInLink = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "linked.MD";
            }
        };

        assertThat(MarkdownLinkRules.isMarkdownResource("linked", withoutExtensionInLink)).isTrue();
    }
}


