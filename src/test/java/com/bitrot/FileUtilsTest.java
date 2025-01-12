package com.bitrot;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import static com.bitrot.FileUtils.computeCRC;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileUtilsTest {
    @Test
    void testComputeCRC() throws IOException {
        final String str = "test1";
        final InputStream inputStream = new ByteArrayInputStream(str.getBytes());
        final Long crc = computeCRC(inputStream);
        assertEquals(2326977762L, crc);
    }

    @Test
    void testCalculateFileId() throws NoSuchAlgorithmException {
        final String filePath = "Some Folder\\Some File.txt";

        FileRecord fileRecord = new FileRecord(null, filePath);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());

        // File ID should NOT be influenced by the absolute file path
        fileRecord = new FileRecord(Paths.get("C:\\" + filePath), filePath);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());
    }
}
