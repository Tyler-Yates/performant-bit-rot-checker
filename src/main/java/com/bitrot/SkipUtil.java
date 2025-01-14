package com.bitrot;

import com.bitrot.data.FileRecord;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.sql.*;
import java.time.Instant;
import java.util.regex.Pattern;

import static com.bitrot.data.Constants.*;

/**
 * Responsible for checking if we need to check a file, or we can skip it because it has already been checked recently.
 */
public class SkipUtil {
    private static final String TABLE_NAME = "file_verification";
    private static final String FILE_NAME = TABLE_NAME + ".sqlite";
    private static final String PATH_SEPARATOR = FileSystems.getDefault().getSeparator();

    private final Connection connection;

    public SkipUtil() {
        this(null);
    }

    public SkipUtil(@Nullable final Connection connection) {
        try {
            if (connection == null) {
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + FILE_NAME);
            } else {
                this.connection = connection;
            }

            initializeTable();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to initialize file verification database", e);
        }

        System.out.println(numRows() + " existing rows in the recent verification database");
    }

    private int numRows() {
        final String query = "SELECT COUNT(*) AS total FROM " + TABLE_NAME;

        try (final PreparedStatement statement = connection.prepareStatement(query);
             final ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getInt("total");
            }

        } catch (final Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Initialize the SQLite table if necessary.
     * <p>
     * {@code absolute_file_path} is the primary key and is the string path to the file<br>
     * {@code modified_time_s} is a long representing the modified time in epoch seconds of the file on disk<br>
     * {@code last_verified} is the timestamp when we last verified this file
     *
     * @throws SQLException if there was an SQL error
     */
    private void initializeTable() throws SQLException {
        try (final Statement stmt = connection.createStatement()) {
            final String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "absolute_file_path TEXT PRIMARY KEY, " +
                    "modified_time_s INTEGER, " +
                    "last_verified TIMESTAMP)";
            stmt.execute(createTable);
        }
    }

    /**
     * Cleans the database by removing records where last_verified is older than the threshold.
     */
    public void cleanDatabase() {
        final String deleteOldRecordsSQL = "DELETE FROM " + TABLE_NAME + " WHERE last_verified < ?";
        try (final PreparedStatement stmt = connection.prepareStatement(deleteOldRecordsSQL)) {
            stmt.setTimestamp(1, Timestamp.from(DELETE_RECENCY_ENTRIES_OLDER_THAN));
            final int rowsDeleted = stmt.executeUpdate();
            System.out.println("Cleaned up " + rowsDeleted + " old records from the database.");
        } catch (final SQLException e) {
            System.err.println("Error during database cleanup");
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns whether the given file should be skipped because it was verified recently or matches a filter.
     *
     * @param fileRecord the file record
     * @return True if the file path should be skipped, False otherwise
     */
    public boolean shouldSkipFile(final FileRecord fileRecord) {
        // Check each part of the path for the filters
        for (final String part : fileRecord.getAbsoluteFilePath().toString().split(Pattern.quote(PATH_SEPARATOR))) {
            for (String prefix : SKIP_PREFIXES) {
                if (part.startsWith(prefix)) {
                    return true;
                }
            }

            for (String suffix : SKIP_SUFFIXES) {
                if (part.endsWith(suffix)) {
                    return true;
                }
            }
        }

        // Now check the SQLite database for whether we should skip this file or not
        try (final PreparedStatement stmt = connection.prepareStatement("SELECT last_verified, modified_time_s FROM " + TABLE_NAME + " WHERE absolute_file_path = ?")) {
            stmt.setString(1, String.valueOf(fileRecord.getAbsoluteFilePath()));
            final ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // If the file has been modified since we last verified it, we should check it regardless of time elapsed.
                final long modifiedTimestampAtLastVerification = rs.getLong("modified_time_s");
                if (modifiedTimestampAtLastVerification != fileRecord.getMTimeSeconds()) {
                    System.out.println("File " + fileRecord.getLogIdentifier() + " has been modified so will be checked.");
                    return false;
                }

                // Skip files that were verified recently.
                final Instant lastVerified = rs.getTimestamp("last_verified").toInstant();
                return lastVerified.isAfter(SKIP_FILES_CHECKED_SINCE);
            } else {
                return false;
            }
        } catch (final SQLException | IOException e) {
            System.err.println("Error getting last_verified, modified_time_s for file with absolute path " + fileRecord.getAbsoluteFilePath());
            throw new RuntimeException(e);
        }
    }

    /**
     * Record that we have verified the file represented by the given record so that we do not check the same path again until the threshold.
     *
     * @param fileRecord the record
     */
    public void recordVerification(final FileRecord fileRecord) {
        try (final PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_NAME + " (absolute_file_path, modified_time_s, last_verified) VALUES (?, ?, ?)")) {
            stmt.setString(1, String.valueOf(fileRecord.getAbsoluteFilePath()));
            stmt.setLong(2, fileRecord.getMTimeSeconds());
            stmt.setTimestamp(3, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (final SQLException | IOException e) {
            System.err.println("Error getting last_verified for file with absolute path " + fileRecord.getAbsoluteFilePath());
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns whether the file record is too new to save to the database or not.
     *
     * @param fileRecord the file record
     * @return True if it is too new, otherwise False
     */
    public static boolean fileIsTooNewToSaveToDatabase(final FileRecord fileRecord) {
        try {
            final Instant creationTime = fileRecord.getFileCreationTime();
            return creationTime.isAfter(DO_NOT_SAVE_FILES_NEWER_THAN);
        } catch (final IOException e) {
            System.err.println("Error calculating fileIsTooNewToSaveToDatabase() for file " + fileRecord.getAbsoluteFilePath());
            throw new RuntimeException(e);
        }
    }
}
