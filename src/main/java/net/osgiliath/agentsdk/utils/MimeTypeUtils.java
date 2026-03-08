package net.osgiliath.agentsdk.utils;

import dev.langchain4j.data.message.*;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.message.ResourceLinkContent;
import org.springframework.http.MediaType;

import java.util.Collection;
import java.util.Set;

public class MimeTypeUtils {
    private static final Collection<String> TEXTUAL_MIME_TYPES = Set.of(
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            "application/javascript",
        MediaType.TEXT_PLAIN_VALUE,
        MediaType.TEXT_HTML_VALUE,
        MediaType.APPLICATION_YAML_VALUE,
        MediaType.TEXT_MARKDOWN_VALUE,
        MediaType.TEXT_XML_VALUE,
        MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        MediaType.APPLICATION_XHTML_XML_VALUE,
        "image/svg+xml"
    );

    public static boolean isTextualMimeType(String mimeType) {
        if (mimeType == null) return false;
        mimeType = mimeType.toLowerCase().split(";")[0].trim(); // remove parameters

        if (mimeType.startsWith("text/")) {
            return true;
        }
        return TEXTUAL_MIME_TYPES.contains(mimeType);
    }
    public static boolean isImageMimeType(String mimeType) {
        return MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.IMAGE_JPEG) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.IMAGE_PNG) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.IMAGE_GIF);
    }

    public static boolean isPdfMimeType(String mimeType) {
        return MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.APPLICATION_PDF);
    }
    public static boolean isVideoMimeType(String mimeType) {
        return MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("video/mp4")) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("video/webm")) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("video/ogg"));
    }
    public static boolean isAudioMimeType(String mimeType) {
        return MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("audio/mpeg")) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("audio/wav")) ||
                MediaType.parseMediaType(mimeType).isCompatibleWith(MediaType.valueOf("audio/ogg"));
    }

    public static Content toContent(ResourceLinkContent resourceLinkContent, byte[] data) throws UnsupportedMimeTypeException {

        if (MimeTypeUtils.isTextualMimeType(resourceLinkContent.mimeType())) {
            StringBuilder sb = new StringBuilder();
            String name = resourceLinkContent.name();
            sb.append("---- File ----").append(" ---\n");
            sb.append("--- Metadata ---").append("\n");
            sb.append("Name: ").append(name).append(" ---\n");
            sb.append("Uri: ").append(resourceLinkContent.uri()).append(" ---\n");
            sb.append("MimeType: ").append(resourceLinkContent.mimeType()).append(" ---\n");
            sb.append("--- Metadata End ---").append("\n");
            sb.append("--- Content Start ---").append("\n");
            sb.append(new String(data)).append("\n");
            sb.append("--- Content End ---");
            sb.append("---- End file ----").append(" ---\n");
            return TextContent.from(sb.toString());
        } else if (MimeTypeUtils.isPdfMimeType(resourceLinkContent.mimeType())) {
            return PdfFileContent.from(resourceLinkContent.uri().toString());
        } else if (MimeTypeUtils.isImageMimeType(resourceLinkContent.mimeType())) {
            return ImageContent.from(resourceLinkContent.uri().toString());
        } else if (MimeTypeUtils.isVideoMimeType(resourceLinkContent.mimeType())) {
            return VideoContent.from(resourceLinkContent.uri().toString());
        } else if (MimeTypeUtils.isAudioMimeType(resourceLinkContent.mimeType())) {
            return AudioContent.from(resourceLinkContent.uri().toString());
        } else {
            throw new UnsupportedMimeTypeException(resourceLinkContent.mimeType());
        }
    }
}
