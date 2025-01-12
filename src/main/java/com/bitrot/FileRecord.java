package com.bitrot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static com.bitrot.FileUtils.*;

public final class FileRecord {
    private final String filePath;
    private final Path absoluteFilePath;
    private String fileId;

    private Instant modifiedTime = null;
    private Instant createdTime = null;
    private Long crc = null;
    private Long size = null;

    /**
     * We distinguish between teh absolute file path and the relative file path.
     * This allows us to verify files on different machines saved on different drives.
     * {@code filePath} should be used for any logic that interacts with the database.
     * {@code absoluteFilePath} should be used for any logic that interacts with the disk.
     * <p>
     * For example:<br>
     * {@code absoluteFilePath} C:\Files\Dir1\File1.txt<br>
     * {@code filePath}         Dir1\File1.txt
     *
     * @param absoluteFilePath the absolute path of the file
     * @param filePath         the relative path of the file ignoring the prefix
     */
    public FileRecord(final Path absoluteFilePath, final String filePath) {
        this.filePath = filePath;
        this.absoluteFilePath = absoluteFilePath;
    }

    /**
     * Returns the path of the file. This is NOT the absolute path to the file on disk!
     *
     * @return the path of the file
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Returns the absolute file path which is where this exists on disk for the current machine.
     *
     * @return the absolute file path
     */
    public Path getAbsoluteFilePath() {
        return absoluteFilePath;
    }

    /**
     * Returns the ID of the file which is a hash of the file path.
     *
     * @return the file ID
     */
    public String getFileId() {
        if (fileId == null) {
            fileId = calculateFileId(filePath);
        }

        return fileId;
    }

    /**
     * Returns the CRC of the file.
     * This file is calculated lazily and only once. This means that if the file is modified during program execution
     * there could be problems!
     *
     * @return the CRC as a string
     * @throws IOException if there was a problem calculating the CRC
     */
    public Long getCrc() throws IOException {
        if (crc == null) {
            crc = computeCRC(absoluteFilePath);
        }
        return crc;
    }

    /**
     * Returns the modified time of the file as an Instant.
     * This value is calculated lazily and only once.
     *
     * @return the modified time as an Instant
     * @throws IOException if there was a problem getting the timestamp
     */
    public Instant getModifiedInstant() throws IOException {
        if (modifiedTime == null) {
            final FileTime modifiedFileTime = Files.getLastModifiedTime(absoluteFilePath);
            modifiedTime = modifiedFileTime.toInstant();
        }
        return modifiedTime;
    }

    /**
     * Returns the modified time (mtime) of the file as a double.
     * This is used when talking to the database.
     * This calls {@link #getModifiedInstant()} under the hood.
     *
     * @return the modified time as a double
     * @throws IOException if there was a problem getting the timestamp
     */
    public double getMTime() throws IOException {
        return instantToDouble(getModifiedInstant());
    }

    /**
     * Get the creation time of the file as an Instant.
     * This value is calculated lazily and only once.
     *
     * @return the creation time
     * @throws IOException if there was a problem getting the timestamp
     */
    public Instant getFileCreationTime() throws IOException {
        if (createdTime == null) {
            final FileTime fileCreatedTime = Files.readAttributes(absoluteFilePath, BasicFileAttributes.class).creationTime();
            createdTime = fileCreatedTime.toInstant();
        }
        return createdTime;
    }

    /**
     * Get the size of the file in bytes.
     * This value is calculated lazily and only once.
     *
     * @return the size in bytes
     * @throws IOException if there was a problem getting the size
     */
    public long getSize() throws IOException {
        if (size == null) {
            size = Files.size(absoluteFilePath);
        }
        return size;
    }
}
