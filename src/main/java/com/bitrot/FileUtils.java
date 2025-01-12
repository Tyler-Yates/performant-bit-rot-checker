package com.bitrot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Formatter;
import java.util.zip.CRC32;

public abstract class FileUtils {
    private static final int BUFFER_SIZE = 8192;

    public static Long computeCRC(final Path filePath) throws IOException {
        try (final FileInputStream fis = new FileInputStream(filePath.toFile())) {
            return computeCRC(fis);
        }
    }

    public static Long computeCRC(final InputStream inputStream) throws IOException {
        final CRC32 crc = new CRC32();
        final byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            crc.update(buffer, 0, bytesRead);
        }
        return crc.getValue();
    }

    public static String calculateFileId(final String filePath) {
        final MessageDigest digest;
        // We should not have to worry about SHA-256 not being found
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] hashBytes = digest.digest(filePath.getBytes());
        return bytesToHex(hashBytes).toLowerCase();
    }

    private static String bytesToHex(byte[] bytes) {
        try (Formatter formatter = new Formatter()) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }

    /**
     * Returns the file path given the absolute path to a file and its prefix path from config.
     *
     * @param absoluteFilePath the absolute path
     * @param configPrefix the prefix path from config
     * @return the file path
     * @throws IllegalArgumentException if the absolute path does not start with the prefix
     */
    public static String getFilePathFromAbsolutePath(final Path absoluteFilePath, final Path configPrefix) {
        if (absoluteFilePath.startsWith(configPrefix)) {
            // Remove the prefix and return the remaining path as a string
            return configPrefix.relativize(absoluteFilePath).toString();
        } else {
            throw new IllegalArgumentException("Absolute path " + absoluteFilePath + " does not start with " + configPrefix);
        }
    }

    /**
     * The old version of this program used Python and os.path.getmtime returns a float. Since Java uses Instant, we
     * need a way to convert that to double or have to redo the whole existing database.
     *
     * @param instant the instant to convert
     * @return the corresponding double value
     */
    public static double instantToDouble(final Instant instant) {
        final long epochSeconds = instant.getEpochSecond();
        final int nanoAdjustment = instant.getNano();

        // Convert epoch seconds to BigDecimal to avoid losing precision
        final BigDecimal bigSeconds = BigDecimal.valueOf(epochSeconds);
        // Convert nanoseconds to microseconds (truncate the remainder) because this is what Python did
        final BigDecimal bigMicros = BigDecimal.valueOf(nanoAdjustment).divide(BigDecimal.valueOf(1_000), 0, RoundingMode.DOWN);

        // Return the result as a native double
        final BigDecimal result = new BigDecimal(bigSeconds + "." + bigMicros);
        return result.doubleValue();
    }
}
