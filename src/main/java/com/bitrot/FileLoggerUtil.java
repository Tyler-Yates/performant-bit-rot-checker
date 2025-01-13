package com.bitrot;

import java.io.IOException;
import java.nio.file.*;
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
    public static void log(final String message) {
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
        } catch (final IOException e) {
            System.err.println("Failed to log message: " + e.getMessage());
        }
    }
}
