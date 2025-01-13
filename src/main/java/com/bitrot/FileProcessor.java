package com.bitrot;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.bitrot.Constants.THREADS;
import static com.bitrot.FileUtils.getFilePathFromAbsolutePath;

public class FileProcessor {
    private final SkipUtil skipUtil;
    private final MongoManager mongoManager;
    private final Map<Result, Integer> resultTotals;

    private ExecutorService executor;

    public FileProcessor(final SkipUtil skipUtil, final MongoManager mongoManager) {
        this.skipUtil = skipUtil;
        this.mongoManager = mongoManager;

        resultTotals = new HashMap<>();
    }

    public void processFiles(final Path directoryPath, final boolean isImmutable) {
        if (isImmutable) {
            System.out.println("Processing immutable path " + directoryPath);
        } else {
            System.out.println("Processing mutable path " + directoryPath);
        }

        executor = Executors.newFixedThreadPool(THREADS);
        final List<Future<FileResult>> futures = new ArrayList<>();

        try (final Stream<Path> paths = Files.walk(directoryPath)) {
            paths.filter(Files::isRegularFile)  // Only process regular files
                    .forEach(path -> {
                        // Submit the job to an executor so we are not bottlenecked by all the MongoDB network calls.
                        // The heavy disk work is synchronized so we do not have to worry about thrashing HDDs.
                        final Future<FileResult> future = processFile(path, directoryPath, isImmutable);
                        if (future != null) {
                            futures.add(future);
                        }
                    });
        } catch (final Exception e) {
            FileLoggerUtil.logException(e);
        } finally {
            // This prevents new tasks from being submitted to the executor, but it still allows in-progress things to finish.
            executor.shutdown();
        }

        // Process all the Futures and add the results to the total counts.
        // We should not need to call executor.awaitTermination() because every Future will have its get() method called,
        // meaning that every task should be complete at the end of this for loop.
        for (final Future<FileResult> future : futures) {
            try {
                final FileResult result = future.get();
                resultTotals.put(result.result(), resultTotals.getOrDefault(result.result(), 0) + 1);
            } catch (final InterruptedException | ExecutionException e) {
                FileLoggerUtil.logException(e);
            }
        }
    }

    /**
     * Log the result totals to the log files.
     */
    public void logTotals() {
        FileLoggerUtil.log("--------------------------");
        FileLoggerUtil.log("Totals:");
        FileLoggerUtil.log("PASS: " + resultTotals.getOrDefault(Result.PASS, 0) + " files");
        FileLoggerUtil.log("FAIL: " + resultTotals.getOrDefault(Result.FAIL, 0) + " files");
        FileLoggerUtil.log("SKIP: " + resultTotals.getOrDefault(Result.SKIP, 0) + " files");
    }

    /**
     * Returns whether this run had no failures.
     *
     * @return true if there were no failures, false otherwise
     */
    public boolean noFailures() {
        return resultTotals.getOrDefault(Result.FAIL, 0) == 0;
    }

    @Nullable
    private Future<FileResult> processFile(final Path absoluteFilePath, final Path configPrefix, final boolean isImmutable) {
        try {
            final String filePath = getFilePathFromAbsolutePath(absoluteFilePath, configPrefix);
            // Preload the fields to be nice to the disk
            final FileRecord fileRecord = new FileRecord(absoluteFilePath, filePath, true);

            return executor.submit(() -> getResult(fileRecord, isImmutable));
        } catch (final Exception e) {
            FileLoggerUtil.logException(e);
            return null;
        }
    }

    private FileResult getResult(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        if (skipUtil.shouldSkipFile(fileRecord)) {
            final String message = "Skipping file " + fileRecord.getAbsoluteFilePath();
            System.out.println(message);
            return new FileResult(Result.SKIP, message);
        }

        final FileResult result = mongoManager.processFileRecord(fileRecord, isImmutable);
        final String message = result.result() + ": " + result.message();

        if (result.result() == Result.PASS) {
            System.out.println(message);
            // Only record successful verifications to the skip util
            skipUtil.recordVerification(fileRecord);
        } else if (result.result() == Result.FAIL) {
            // Log failures to disk so we can triage them
            FileLoggerUtil.log(message);
        } else {
            System.out.println(message);
        }
        return result;
    }
}
