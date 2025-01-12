package com.bitrot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileProcessor {
    private final RecencyManager dbManager;
    private final MongoManager mongoManager;

    public FileProcessor(final RecencyManager dbManager, final MongoManager mongoManager) {
        this.dbManager = dbManager;
        this.mongoManager = mongoManager;
    }

    public void processFiles(final String directoryPath) {
        final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            Files.walk(Paths.get(directoryPath)).filter(Files::isRegularFile).forEach(file -> {
                executor.submit(() -> processFile(file));
            });
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private void processFile(final Path filePath) {
//        try {
//            final LocalDateTime lastVerified = dbManager.getLastVerified(filePath.toString());
//            if (lastVerified != null && lastVerified.isAfter(LocalDateTime.now().minusDays(90))) {
//                return;
//            }
//
//            final String computedCrc = FileUtils.computeCRC(filePath);
//            final String storedCrc = mongoManager.getStoredCRC(filePath.toString());
//
//            if (computedCrc != null && computedCrc.equals(storedCrc)) {
//                dbManager.updateRecord(new FileRecord(filePath.toString(), computedCrc, LocalDateTime.now()));
//            } else {
//                System.err.println("CRC mismatch for file: " + filePath);
//            }
//        } catch (final Exception e) {
//            e.printStackTrace();
//        }
    }

    public static boolean fileExists(final String filePath) {
        return Files.exists(Paths.get(filePath));
    }
}
