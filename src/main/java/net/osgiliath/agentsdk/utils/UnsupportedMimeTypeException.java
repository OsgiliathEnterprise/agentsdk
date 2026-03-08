package net.osgiliath.agentsdk.utils;

/**
 * Exception thrown when an unsupported MIME type is encountered.
 */
public class UnsupportedMimeTypeException extends Throwable {

    /**
     * Creates a new instance of UnsupportedMimeTypeException with the specified detail message.
     * @param s the detail message explaining the reason for the exception
     */
    public UnsupportedMimeTypeException(String s) {
        super(s);
    }
}
