package com.bitrot;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Instant;

import static com.bitrot.FileUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileUtilsTest {
    @Test
    void testComputeCRC() throws IOException {
        final String str = "test1";
        final InputStream inputStream = new ByteArrayInputStream(str.getBytes());
        final Long crc = computeCRC(inputStream);
        assertEquals(2326977762L, crc);
    }

    @Test
    void testCalculateFileId() {
        final String filePath = "Some Folder\\Some File.txt";

        FileRecord fileRecord = new FileRecord(null, filePath);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());

        // File ID should NOT be influenced by the absolute file path
        fileRecord = new FileRecord(Paths.get("C:\\" + filePath), filePath);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());
    }

    @Test
    void testGetFilePathFromAbsolutePath() {
        String filePath = getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("C:\\"));
        assertEquals("dir1\\file1.txt", filePath);

        filePath = getFilePathFromAbsolutePath(Paths.get("N:\\dir 1\\dir 2\\file 1.txt"), Paths.get("N:\\dir 1"));
        assertEquals("dir 2\\file 1.txt", filePath);
    }

    @Test
    void testGetFilePathFromAbsolutePathError() {
        // The absolute file path does not start with the prefix so this should be an error
        assertThrows(IllegalArgumentException.class, () -> getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("Z:\\")));
        assertThrows(IllegalArgumentException.class, () -> getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("C:\\dir2")));
    }

    @Test
    void testInstantToDouble() {
        final Instant instant = Instant.parse("2025-01-04T04:44:31.816606100Z");
        assertEquals(1735965871.816606, instantToDouble(instant));
    }
}
