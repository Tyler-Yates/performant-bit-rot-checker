package com.bitrot;

import org.bson.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.bitrot.FileUtils.getFilePathFromAbsolutePath;

public class FileProcessor {
    private final RecencyManager recencyManager;
    private final MongoManager mongoManager;

    public FileProcessor(final RecencyManager recencyManager, final MongoManager mongoManager) {
        this.recencyManager = recencyManager;
        this.mongoManager = mongoManager;
    }

    public void processFiles(final Path directoryPath, final boolean isImmutable) {
//        final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//        try {
//            Files.walk(Paths.get(directoryPath)).filter(Files::isRegularFile).forEach(file -> {
//                executor.submit(() -> processFile(file));
//            });
//        } catch (final Exception e) {
//            e.printStackTrace();
//        } finally {
//            executor.shutdown();
//        }

        try {
            Files.walk(directoryPath).filter(Files::isRegularFile).forEach(absoluteFilePath -> this.processFile(absoluteFilePath, directoryPath));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processFile(final Path absoluteFilePath, final Path configPrefix) {
        try {
            final String filePath = getFilePathFromAbsolutePath(absoluteFilePath, configPrefix);
            final FileRecord fileRecord = new FileRecord(absoluteFilePath, filePath);
            if (recencyManager.shouldSkipFile(fileRecord)) {
                System.out.println("Skipping file " + absoluteFilePath);
                return;
            }

            final Document databaseDocument = findDocument();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
