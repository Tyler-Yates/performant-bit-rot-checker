package com.bitrot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static com.bitrot.FileUtils.calculateFileId;
import static com.bitrot.FileUtils.computeCRC;

public final class FileRecord {
    private final String filePath;
    private final Path absoluteFilePath;

    // Lazy fields that are low-cost on the disk
    private Instant modifiedTime = null;
    private Instant createdTime = null;
    private Long crc = null;

    // Lazy fields we don't want to preload
    private Long size = null;
    private String fileId = null;

    /**
     * We distinguish between the absolute file path and the relative file path.
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
     * @param preLoad         if true, will preload the optional fields besides CRC and fileID
     */
    public FileRecord(final Path absoluteFilePath, final String filePath, final boolean preLoad) {
        this.filePath = filePath;
        this.absoluteFilePath = absoluteFilePath;

        if (preLoad) {
            try {
                getModifiedInstant();
                getFileCreationTime();
                getSize();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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

    public String getLogIdentifier() {
        return "(" + absoluteFilePath + ", " + getFileId() + ")";
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
     * Returns the checksum (CRC) of the file.
     * This file is calculated lazily and only once. This means that if the file is modified during program execution
     * there could be problems!
     *
     * @return the CRC as a string
     * @throws IOException if there was a problem calculating the CRC
     */
    public Long getChecksum() throws IOException {
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
     * Returns the modified time (mtime) of the file as a long representing the seconds since the epoch.
     * Not all file systems have the same resolution and Python doesn't have the same precision as Java.
     * Use second resolution as this is good enough and should work everywhere.
     * <p>
     * This is used when talking to the database.
     * This calls {@link #getModifiedInstant()} under the hood.
     *
     * @return the modified time as a long
     * @throws IOException if there was a problem getting the timestamp
     */
    public long getMTimeSeconds() throws IOException {
        return getModifiedInstant().getEpochSecond();
    }

    /**
     * Returns the nanosecond part of the modified time (mtime) of the file.
     * This method goes hand-in-hand with {@link #getMTimeSeconds()}.
     * <p>
     * This is used when talking to the database.
     * This calls {@link #getModifiedInstant()} under the hood.
     *
     * @return the modified time as a long
     * @throws IOException if there was a problem getting the timestamp
     */
    public int getMTimeNanos() throws IOException {
        return getModifiedInstant().getNano();
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
