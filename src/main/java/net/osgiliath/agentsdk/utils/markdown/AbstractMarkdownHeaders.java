package net.osgiliath.agentsdk.utils.markdown;

import java.util.List;
import java.util.Optional;

public class AbstractMarkdownHeaders implements MarkdownHeaders {

    private final List<MarkdownHeader> headers;

    public AbstractMarkdownHeaders(List<MarkdownHeader> headers) {
        this.headers = headers;
    }
    
    @Override
    public List<String> headerKeys() {
        return headers.stream().map(MarkdownHeader::key).toList();
    }

    @Override
    public Optional<Object> header(String headerKey) {
        return headers.stream().filter(h -> h.key().equals(headerKey)).map(MarkdownHeader::value).findAny();
    }
}
