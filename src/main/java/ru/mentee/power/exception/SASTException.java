/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class SASTException extends RuntimeException {
    public SASTException(String message) {
        super(message);
    }

    public SASTException(String message, Throwable cause) {
        super(message, cause);
    }
}
