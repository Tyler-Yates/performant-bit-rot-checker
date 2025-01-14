package com.bitrot;

import com.bitrot.data.FileRecord;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.bitrot.FileUtils.computeCRC;
import static com.bitrot.FileUtils.getFilePathFromAbsolutePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileUtilsTest {
    @Test
    void testComputeCRC() throws IOException {
        // Create a temporary in-memory file system
        try (final FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
            final Path path = fs.getPath("test1.txt");
            Files.write(path, "test1".getBytes());

            assertEquals(2326977762L, computeCRC(path));
        }
    }

    @Test
    void testCalculateFileId() {
        final String filePath = "Some Folder\\Some File.txt";

        FileRecord fileRecord = new FileRecord(null, filePath, false);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());

        // File ID should NOT be influenced by the absolute file path
        fileRecord = new FileRecord(Paths.get("C:\\" + filePath), filePath, false);
        assertEquals("2f8f4b58d30d87cf5b95e7d1d17c971b5924fb1a041c6620e8c262c47beb4b26", fileRecord.getFileId());
    }

    @Test
    void testGetFilePathFromAbsolutePath() {
        String filePath = getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("C:\\"));
        assertEquals("\\dir1\\file1.txt", filePath);

        filePath = getFilePathFromAbsolutePath(Paths.get("N:\\dir 1\\dir 2\\file 1.txt"), Paths.get("N:\\dir 1"));
        assertEquals("\\dir 2\\file 1.txt", filePath);
    }

    @Test
    void testGetFilePathFromAbsolutePathError() {
        // The absolute file path does not start with the prefix so this should be an error
        assertThrows(IllegalArgumentException.class, () -> getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("Z:\\")));
        assertThrows(IllegalArgumentException.class, () -> getFilePathFromAbsolutePath(Paths.get("C:\\dir1\\file1.txt"), Paths.get("C:\\dir2")));
    }
}
