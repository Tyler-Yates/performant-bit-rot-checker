package com.bitrot;

import java.io.IOException;
import java.nio.file.Paths;

public class FileValidator {
    public static void main(final String[] args) throws IOException {
        final Config config = Config.readConfig();

        final RecencyManager recencyManager = new RecencyManager();
        final MongoManager mongoManager = new MongoManager(config.getMongoConnectionString());

        // Clean up the database before we start
        recencyManager.cleanDatabase();

        final FileProcessor processor = new FileProcessor(recencyManager, mongoManager);

        // Go through the mutable paths first
        for (final String mutablePath : config.getMutablePaths()) {
            processor.processFiles(Paths.get(mutablePath), false);
        }

        // Then the immutable paths
        for (final String immutablePath : config.getImmutablePaths()) {
            processor.processFiles(Paths.get(immutablePath), true);
        }
    }
}
