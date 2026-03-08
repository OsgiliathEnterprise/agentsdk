# Test Dataset Files

This directory contains minimal test files for validating ACP ResourceLinks attachment support.

## Files

### 1. Thread.java (Easter Egg + AST Testing)
- **Purpose**: Test Java file attachment and AST parsing
- **Easter Egg**: Contains the secret code "CUCUMBER_BDD_ROCKS_2026" in comments
- **Use Case**: Validates that LLM can read and extract content from attached files
- **MIME Type**: `text/java` or `text/x-java-source`

### 2. test-image.png (Image Testing)
- **Purpose**: Test image file attachment
- **Format**: Valid PNG image (1x1 red pixel)
- **MIME Type**: `image/png`
- **Use Case**: Validates binary image file handling

### 3. test-document.pdf (PDF Testing)
- **Purpose**: Test PDF document attachment
- **Format**: Minimal valid PDF with text "Test PDF"
- **MIME Type**: `application/pdf`
- **Use Case**: Validates PDF document handling

### 4. test-archive.zip (Archive Testing)
- **Purpose**: Test ZIP archive attachment and nested file references
- **Format**: ZIP archive containing a text file
- **MIME Type**: `application/zip`
- **Use Case**: Validates archive handling and URIs like `file:///path/to/archive.zip!/internal/path`

### 5. test-video.webm (Video Testing)
- **Purpose**: Test video file attachment
- **Format**: Placeholder for video content (mock in tests)
- **MIME Type**: `video/webm`
- **Use Case**: Validates video file handling

### 6. config.json (Simple JSON)
- **Purpose**: Test JSON configuration file attachment
- **Format**: Simple JSON configuration
- **MIME Type**: `application/json`
- **Use Case**: Basic structured data file testing

## Usage in Tests

These files are referenced in `attachment-support.feature` scenarios to test:
- Single and multiple file attachments
- Different MIME types
- File URI schemes
- Archive references with nested paths
- LLM content reading validation (easter egg)
- AST parsing capabilities (Java file)

