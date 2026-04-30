package net.osgiliath.agentsdk.utils.resource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ResourceLocationResolverTest {

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Test
    void testResolveLocation() {
        // This test would require a mock ResourcePatternResolver and some setup to verify the behavior of resolveLocation.
        // For example, you could use Mockito to create a mock ResourcePatternResolver and define its behavior for certain patterns.
        // Then, you would call resolveLocation with different location strings and assert that the returned Optional<Resource> is as expected.
        ResourceLocationResolver resolver = new ResourceLocationResolverImpl(resourcePatternResolver);
        // Null input is rejected by contract.
        assertThatThrownBy(() -> resolver.resolveLocation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("location must not be null");
        // Test empty input
        assertThat(resolver.resolveLocation("")).isEmpty();
        // Test unexisting location
        assertThat(resolver.resolveLocation("classpath*:nonexistent/**")).isEmpty();
        // Test existing location (this would require an actual resource to be present in the classpath or file system)
        assertThat(resolver.resolveLocation("classpath:dataset/README.md")).isPresent()
                .containsInstanceOf(Resource.class);

    }
}
