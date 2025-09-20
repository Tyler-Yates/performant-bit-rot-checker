package com.bitrot;

import com.bitrot.data.Config;
import com.bitrot.data.DatabaseDocument;
import com.bitrot.data.FileRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class FixTimestamp {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java -jar your-jar.jar <absoluteFilePath> <prefix>");
            System.exit(1);
        }
        
        final String absoluteFilePath = args[0];
        final String prefix = args[1];
        final Config config = Config.readConfig();
        final MongoManager mongoManager = new MongoManager(config.getMongoConnectionString());
        
        final String filePath = absoluteFilePath.replace(prefix, "");
        System.out.println(filePath);

        final Path absoluteFilePathObj = Path.of(absoluteFilePath);
        final FileRecord fileRecord = new FileRecord(absoluteFilePathObj, filePath, true);
        System.out.println(fileRecord);

        final DatabaseDocument document = mongoManager.findDocument(fileRecord, true);
        if (document == null) {
            System.out.println("DB Document not found");
            System.exit(1);
        }
        final FileTime fileTime = FileTime.from(Instant.ofEpochSecond(document.mTimeSeconds(), document.mTimeNanos()));
        Files.setLastModifiedTime(absoluteFilePathObj, fileTime);
        System.out.println("Fixed timestamp to be: " + fileTime);
    }
}
