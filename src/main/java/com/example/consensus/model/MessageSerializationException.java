package com.example.consensus.model;

/**
 * Exception thrown when message serialization or deserialization fails.
 */
public class MessageSerializationException extends RuntimeException {
    
    /**
     * Constructs a new MessageSerializationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public MessageSerializationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new MessageSerializationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause
     */
    public MessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new MessageSerializationException with the specified cause.
     * 
     * @param cause the cause
     */
    public MessageSerializationException(Throwable cause) {
        super(cause);
    }
}