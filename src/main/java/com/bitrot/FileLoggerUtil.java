package com.bitrot;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to log important messages to disk.
 * Most messages should go to standard output to not clutter the log file on disk.
 */
public class FileLoggerUtil {
    private static final Path LOGS_DIR = Paths.get("logs");
    private static final Path LATEST_FILE_PATH = LOGS_DIR.resolve("latest.txt");
    private static final Path DATED_FILE_PATH;
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static boolean encounteredException = false;

    // Initialize the logging infrastructure at program startup
    static {
        try {
            // Ensure the logs directory exists
            if (Files.notExists(LOGS_DIR)) {
                Files.createDirectories(LOGS_DIR);
            }

            // Initialize and clear the latest.txt file
            Files.writeString(LATEST_FILE_PATH, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Set the datetime-named log file
            DATED_FILE_PATH = LOGS_DIR.resolve(LocalDateTime.now().format(FILE_DATE_FORMAT) + ".txt");
            Files.writeString(DATED_FILE_PATH, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (final IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes a message to both "latest.txt" and the datetime-named file as well as stdout.
     *
     * @param message The message to log
     */
    public static void log(@NonNull final String message) {
        try {
            // Write to latest.txt
            Files.writeString(
                    LATEST_FILE_PATH,
                    message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );

            // Write to datetime-named file
            Files.writeString(
                    DATED_FILE_PATH,
                    message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );

            // Print to stdout
            System.out.println("[FileLogger] " + message);
        } catch (final IOException e) {
            System.err.println("Failed to log message: " + e.getMessage());
        }
    }

    /**
     * Logs the given Exception to {@link #log(String)} but only the message and not the full stacktrace.
     * <p>
     * !!! This will cause the program to not call the health check and exit with a non-zero code !!!
     *
     * @param e the exception
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public static void logException(@NonNull final Exception e) {
        encounteredException = true;

        e.printStackTrace();
        log(" [" + e.getClass().getSimpleName() + "] " + e.getMessage());
    }

    /**
     * Returns whether we have encountered an Exception during this program execution.
     *
     * @return true if we have encountered an exception, false otherwise
     */
    public static boolean encounteredException() {
        return encounteredException;
    }
}
