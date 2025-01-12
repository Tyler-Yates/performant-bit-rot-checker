package com.bitrot;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.bitrot.Constants.THREADS;
import static com.bitrot.FileUtils.getFilePathFromAbsolutePath;

public class FileProcessor {
    private final RecencyManager recencyManager;
    private final MongoManager mongoManager;

    private ExecutorService executor;

    public FileProcessor(final RecencyManager recencyManager, final MongoManager mongoManager) {
        this.recencyManager = recencyManager;
        this.mongoManager = mongoManager;
    }

    public void processFiles(final Path directoryPath, final boolean isImmutable) {
        executor = Executors.newFixedThreadPool(THREADS);
        final List<Future<FileResult>> futures = new ArrayList<>();

        try {
            // Walk through the directory and its subdirectories
            try (final Stream<Path> paths = Files.walk(directoryPath)) {
                for (final Path absoluteFilePath : (Iterable<Path>) paths::iterator) {
                    // Only process regular files
                    if (Files.isRegularFile(absoluteFilePath)) {
                        // Submit the job to an executor since the heavy disk work is single threaded
                        final Future<FileResult> future = processFile(absoluteFilePath, directoryPath, isImmutable);
                        if (future != null) {
                            futures.add(future);
                        }
                    }
                }
            }

            // Process all the results
            for (final Future<FileResult> future : futures) {
                final FileResult result = future.get();

                // TODO categorization
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    @Nullable
    private Future<FileResult> processFile(final Path absoluteFilePath, final Path configPrefix, final boolean isImmutable) {
        try {
            final String filePath = getFilePathFromAbsolutePath(absoluteFilePath, configPrefix);
            // Preload the fields to be nice to the disk
            final FileRecord fileRecord = new FileRecord(absoluteFilePath, filePath, true);

            return executor.submit(() -> getResult(fileRecord, isImmutable));
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private FileResult getResult(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        if (recencyManager.shouldSkipFile(fileRecord)) {
            final String message = "Skipping file " + fileRecord.getAbsoluteFilePath();
            System.out.println(message);
            return new FileResult(Result.SKIP, message);
        }

        final FileResult result = mongoManager.processFileRecord(fileRecord, isImmutable);
        System.out.println(result.result() + ": " + result.message());
        if (result.result() == Result.PASS) {
            recencyManager.recordVerification(fileRecord);
        }
        return result;
    }
}
