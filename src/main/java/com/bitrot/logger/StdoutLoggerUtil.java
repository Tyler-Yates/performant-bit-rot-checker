package com.bitrot.logger;

import org.jspecify.annotations.NonNull;

public class StdoutLoggerUtil implements LoggerUtil {
    private boolean encounteredException = false;

    @Override
    public void log(@NonNull final String message) {
        System.out.println("[FileLogger] " + message);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void logException(@NonNull final Exception e) {
        encounteredException = true;
        e.printStackTrace();
    }

    @Override
    public boolean encounteredException() {
        return encounteredException;
    }
}
