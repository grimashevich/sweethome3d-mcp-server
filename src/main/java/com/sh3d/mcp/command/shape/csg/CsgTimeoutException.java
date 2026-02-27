package com.sh3d.mcp.command.shape.csg;

/**
 * Thrown when a CSG Boolean operation exceeds the allowed time limit.
 */
final class CsgTimeoutException extends RuntimeException {

    CsgTimeoutException(String message) {
        super(message);
    }
}
