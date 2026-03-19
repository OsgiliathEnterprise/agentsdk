package net.osgiliath.agentsdk.utils;

import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.message.ResourceLinkContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MimeTypeUtils Tests")
class MimeTypeUtilsTest {

    // ==================== isTextualMimeType ====================

    @Test
    @DisplayName("should identify text/plain as textual")
    void testTextPlainIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("text/plain")).isTrue();
    }

    @Test
    @DisplayName("should identify text/html as textual")
    void testTextHtmlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("text/html")).isTrue();
    }

    @Test
    @DisplayName("should identify text/xml as textual")
    void testTextXmlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("text/xml")).isTrue();
    }

    @Test
    @DisplayName("should identify text/markdown as textual")
    void testTextMarkdownIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("text/markdown")).isTrue();
    }

    @Test
    @DisplayName("should identify application/json as textual")
    void testApplicationJsonIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/json")).isTrue();
    }

    @Test
    @DisplayName("should identify application/xml as textual")
    void testApplicationXmlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/xml")).isTrue();
    }

    @Test
    @DisplayName("should identify application/yaml as textual")
    void testApplicationYamlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/yaml")).isTrue();
    }

    @Test
    @DisplayName("should identify application/javascript as textual")
    void testApplicationJavascriptIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/javascript")).isTrue();
    }

    @Test
    @DisplayName("should identify image/svg+xml as textual")
    void testImageSvgXmlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("image/svg+xml")).isTrue();
    }

    @Test
    @DisplayName("should identify application/x-www-form-urlencoded as textual")
    void testApplicationFormUrlencodedIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/x-www-form-urlencoded")).isTrue();
    }

    @Test
    @DisplayName("should identify application/xhtml+xml as textual")
    void testApplicationXhtmlXmlIsTextual() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/xhtml+xml")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/css",
            "text/csv",
            "text/calendar",
            "text/x-yaml"
    })
    @DisplayName("should identify various text/* types as textual")
    void testVariousTextTypesAreTextual(String mimeType) {
        assertThat(MimeTypeUtils.isTextualMimeType(mimeType)).isTrue();
    }

    @Test
    @DisplayName("should handle mime type with charset parameter")
    void testTextualMimeTypeWithCharsetParameter() {
        assertThat(MimeTypeUtils.isTextualMimeType("text/plain; charset=utf-8")).isTrue();
    }

    @Test
    @DisplayName("should handle mime type with multiple parameters")
    void testTextualMimeTypeWithMultipleParameters() {
        assertThat(MimeTypeUtils.isTextualMimeType("application/json; charset=utf-8; boundary=something")).isTrue();
    }

    @Test
    @DisplayName("should handle case-insensitive mime type check")
    void testCaseInsensitiveMimeTypeCheck() {
        assertThat(MimeTypeUtils.isTextualMimeType("TEXT/PLAIN")).isTrue();
        assertThat(MimeTypeUtils.isTextualMimeType("Application/JSON")).isTrue();
    }

    @Test
    @DisplayName("should handle mime type with whitespace")
    void testMimeTypeWithWhitespace() {
        assertThat(MimeTypeUtils.isTextualMimeType("  text/plain  ")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "image/jpeg",
            "image/png",
            "image/gif",
            "video/mp4",
            "audio/mpeg",
            "application/pdf",
            "application/zip",
            "application/octet-stream"
    })
    @DisplayName("should identify non-textual mime types")
    void testNonTextualMimeTypes(String mimeType) {
        assertThat(MimeTypeUtils.isTextualMimeType(mimeType)).isFalse();
    }

    @Test
    @DisplayName("should return false for null mime type")
    void testNullMimeTypeReturnsFalse() {
        assertThat(MimeTypeUtils.isTextualMimeType(null)).isFalse();
    }

    // ==================== isImageMimeType ====================

    @Test
    @DisplayName("should identify image/jpeg as image")
    void testImageJpegIsImage() {
        assertThat(MimeTypeUtils.isImageMimeType("image/jpeg")).isTrue();
    }

    @Test
    @DisplayName("should identify image/png as image")
    void testImagePngIsImage() {
        assertThat(MimeTypeUtils.isImageMimeType("image/png")).isTrue();
    }

    @Test
    @DisplayName("should identify image/gif as image")
    void testImageGifIsImage() {
        assertThat(MimeTypeUtils.isImageMimeType("image/gif")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/plain",
            "application/json",
            "video/mp4",
            "audio/mpeg",
            "application/pdf"
    })
    @DisplayName("should identify non-image mime types")
    void testNonImageMimeTypes(String mimeType) {
        assertThat(MimeTypeUtils.isImageMimeType(mimeType)).isFalse();
    }

    // ==================== isPdfMimeType ====================

    @Test
    @DisplayName("should identify application/pdf as PDF")
    void testApplicationPdfIsPdf() {
        assertThat(MimeTypeUtils.isPdfMimeType("application/pdf")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/plain",
            "application/json",
            "image/jpeg",
            "video/mp4",
            "audio/mpeg"
    })
    @DisplayName("should identify non-PDF mime types")
    void testNonPdfMimeTypes(String mimeType) {
        assertThat(MimeTypeUtils.isPdfMimeType(mimeType)).isFalse();
    }

    // ==================== isVideoMimeType ====================

    @Test
    @DisplayName("should identify video/mp4 as video")
    void testVideoMp4IsVideo() {
        assertThat(MimeTypeUtils.isVideoMimeType("video/mp4")).isTrue();
    }

    @Test
    @DisplayName("should identify video/webm as video")
    void testVideoWebmIsVideo() {
        assertThat(MimeTypeUtils.isVideoMimeType("video/webm")).isTrue();
    }

    @Test
    @DisplayName("should identify video/ogg as video")
    void testVideoOggIsVideo() {
        assertThat(MimeTypeUtils.isVideoMimeType("video/ogg")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/plain",
            "application/json",
            "image/jpeg",
            "audio/mpeg",
            "application/pdf"
    })
    @DisplayName("should identify non-video mime types")
    void testNonVideoMimeTypes(String mimeType) {
        assertThat(MimeTypeUtils.isVideoMimeType(mimeType)).isFalse();
    }

    // ==================== isAudioMimeType ====================

    @Test
    @DisplayName("should identify audio/mpeg as audio")
    void testAudioMpegIsAudio() {
        assertThat(MimeTypeUtils.isAudioMimeType("audio/mpeg")).isTrue();
    }

    @Test
    @DisplayName("should identify audio/wav as audio")
    void testAudioWavIsAudio() {
        assertThat(MimeTypeUtils.isAudioMimeType("audio/wav")).isTrue();
    }

    @Test
    @DisplayName("should identify audio/ogg as audio")
    void testAudioOggIsAudio() {
        assertThat(MimeTypeUtils.isAudioMimeType("audio/ogg")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "text/plain",
            "application/json",
            "image/jpeg",
            "video/mp4",
            "application/pdf"
    })
    @DisplayName("should identify non-audio mime types")
    void testNonAudioMimeTypes(String mimeType) {
        assertThat(MimeTypeUtils.isAudioMimeType(mimeType)).isFalse();
    }

    // ==================== toContent ====================

    @Test
    @DisplayName("should convert textual content to TextContent with metadata envelope")
    void testToContentForTextualMimeType() throws UnsupportedMimeTypeException {
        ResourceLinkContent resource = resourceLink("notes.md", "text/markdown", "file:///tmp/notes.md");

        Content content = MimeTypeUtils.toContent(resource, "hello markdown".getBytes(StandardCharsets.UTF_8));

        assertThat(content).isInstanceOf(TextContent.class);
        String text = ((TextContent) content).text();
        assertThat(text)
            .contains("---- File ----")
            .contains("Name: notes.md")
            .contains("Uri: file:///tmp/notes.md")
            .contains("MimeType: text/markdown")
            .contains("hello markdown")
            .contains("---- End file ----");
    }

    @Test
    @DisplayName("should map application/pdf to PdfFileContent")
    void testToContentForPdfMimeType() throws UnsupportedMimeTypeException {
        ResourceLinkContent resource = resourceLink("doc.pdf", "application/pdf", "file:///tmp/doc.pdf");

        Content content = MimeTypeUtils.toContent(resource, new byte[]{1, 2, 3});

        assertThat(content).isInstanceOf(PdfFileContent.class);
    }

    @Test
    @DisplayName("should map image mime types to ImageContent")
    void testToContentForImageMimeType() throws UnsupportedMimeTypeException {
        ResourceLinkContent resource = resourceLink("img.png", "image/png", "file:///tmp/img.png");

        Content content = MimeTypeUtils.toContent(resource, new byte[]{1});

        assertThat(content).isInstanceOf(ImageContent.class);
    }

    @Test
    @DisplayName("should map video mime types to VideoContent")
    void testToContentForVideoMimeType() throws UnsupportedMimeTypeException {
        ResourceLinkContent resource = resourceLink("clip.mp4", "video/mp4", "file:///tmp/clip.mp4");

        Content content = MimeTypeUtils.toContent(resource, new byte[]{1});

        assertThat(content).isInstanceOf(VideoContent.class);
    }

    @Test
    @DisplayName("should map audio mime types to AudioContent")
    void testToContentForAudioMimeType() throws UnsupportedMimeTypeException {
        ResourceLinkContent resource = resourceLink("sound.ogg", "audio/ogg", "file:///tmp/sound.ogg");

        Content content = MimeTypeUtils.toContent(resource, new byte[]{1});

        assertThat(content).isInstanceOf(AudioContent.class);
    }

    @Test
    @DisplayName("should throw UnsupportedMimeTypeException for unsupported mime type")
    void testToContentForUnsupportedMimeType() {
        ResourceLinkContent resource = resourceLink("archive.zip", "application/zip", "file:///tmp/archive.zip");

        assertThatThrownBy(() -> MimeTypeUtils.toContent(resource, new byte[]{1}))
            .isInstanceOf(UnsupportedMimeTypeException.class);
    }

    private ResourceLinkContent resourceLink(String name, String mimeType, String uri) {
        ResourceLinkContent resource = mock(ResourceLinkContent.class);
        when(resource.name()).thenReturn(name);
        when(resource.mimeType()).thenReturn(mimeType);
        when(resource.uri()).thenReturn(URI.create(uri));
        return resource;
    }
}

