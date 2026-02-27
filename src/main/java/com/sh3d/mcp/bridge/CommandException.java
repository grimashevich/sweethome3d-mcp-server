package com.sh3d.mcp.bridge;

/**
 * Исключение, возникающее при выполнении команды (EDT-ошибки, бизнес-логика).
 */
public class CommandException extends RuntimeException {

    public CommandException(String message) {
        super(message);
    }

    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
