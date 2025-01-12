package com.bitrot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
}
