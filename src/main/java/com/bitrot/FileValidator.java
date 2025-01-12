package com.bitrot;

public class FileValidator {
    public static void main(final String[] args) {
        final RecencyManager dbManager = new RecencyManager();
        final MongoManager mongoManager = new MongoManager("mongodb://localhost:27017");

        // Clean up the database
        dbManager.cleanDatabase();

        // Process files
        final FileProcessor processor = new FileProcessor(dbManager, mongoManager);
        processor.processFiles("path/to/your/files");
    }
}
