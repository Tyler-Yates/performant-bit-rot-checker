package com.bitrot.logger;

import org.jspecify.annotations.NonNull;

public interface LoggerUtil {
    /**
     * Writes a message to the logging medium.
     *
     * @param message the message
     */
    void log(@NonNull final String message);

    /**
     * Writes an exception to the logging medium.
     *
     * @param e the exception
     */
    void logException(@NonNull final Exception e);

    /**
     * Returns whether this instance has encountered an Exception during this program execution.
     *
     * @return true if we have encountered an exception, false otherwise
     */
    boolean encounteredException();
}
